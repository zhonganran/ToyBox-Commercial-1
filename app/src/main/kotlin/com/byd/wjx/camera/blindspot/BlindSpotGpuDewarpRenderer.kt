package com.byd.wjx.camera.blindspot

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Receives the private AVM stream through an external OES texture and renders a dewarped mesh. */
internal class BlindSpotGpuDewarpRenderer private constructor(
    private val outputTexture: SurfaceTexture,
    private var outputWidth: Int,
    private var outputHeight: Int,
    private var calibration: BlindSpotCalibration,
    private val onFallback: (String) -> Unit,
    private val onMappingApplied: (Boolean) -> Unit,
    private val onFatalError: (Throwable) -> Unit,
) {
    private val thread = HandlerThread("wjx-camera-dewarp")
    private val released = AtomicBoolean(false)
    private val runtimeFailureReported = AtomicBoolean(false)
    private val textureMatrix = FloatArray(16)

    private var handler: Handler? = null
    private var cameraTexture: SurfaceTexture? = null
    var cameraSurface: Surface? = null
        private set
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null
    private var program = 0
    private var textureId = 0
    private var positionLocation = -1
    private var texCoordLocation = -1
    private var textureMatrixLocation = -1
    private var textureLocation = -1
    private var vertices: FloatBuffer? = null
    private var indices: ShortBuffer? = null
    private var indexCount = 0
    @Volatile private var dewarpActive = calibration.dewarpEnabled
    @Volatile private var pendingUpdate: MappingUpdate? = null
    @Volatile private var renderingEnabled = false
    private val mappingUpdatePosted = AtomicBoolean(false)
    private var renderQueued = false
    private var pendingFrameSignals = 0

    val isDewarpActive: Boolean
        get() = dewarpActive

    fun update(value: BlindSpotCalibration, width: Int, height: Int) {
        if (released.get()) return
        pendingUpdate = MappingUpdate(value, width, height)
        scheduleLatestUpdate()
    }

    fun updateInputSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || released.get()) return
        handler?.post { cameraTexture?.setDefaultBufferSize(width, height) }
    }

    fun setRenderingEnabled(value: Boolean) {
        renderingEnabled = value
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        val activeHandler = handler
        handler = null
        if (activeHandler == null) {
            runCatching { outputTexture.release() }
            thread.quitSafely()
            return
        }
        val posted = activeHandler.post {
            try {
                releaseGl()
            } finally {
                runCatching { outputTexture.release() }
                thread.quitSafely()
            }
        }
        if (!posted) {
            runCatching { outputTexture.release() }
            thread.quitSafely()
        }
    }

    private fun start(): Boolean {
        val ready = CountDownLatch(1)
        var startupError: Throwable? = null
        thread.start()
        handler = Handler(thread.looper)
        handler?.post {
            try {
                initializeGl()
            } catch (error: Throwable) {
                startupError = error
            } finally {
                ready.countDown()
            }
        }
        val completed = runCatching {
            ready.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        val surfaceReady = cameraSurface?.isValid == true
        if (completed && startupError == null && surfaceReady) return true
        releaseBlockingAfterStartupFailure()
        onFallback(
            startupError?.let { "GPU startup failed: ${it.javaClass.simpleName}: ${it.message}" }
                ?: "GPU startup timeout",
        )
        return false
    }

    private fun initializeGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        requireEgl(eglDisplay != EGL14.EGL_NO_DISPLAY, "EGL display unavailable")
        requireEgl(EGL14.eglInitialize(eglDisplay, null, 0, null, 0), "EGL init failed")
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,
            EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_ALPHA_SIZE,
            8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        requireEgl(
            EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, count, 0) &&
                count[0] > 0,
            "EGL config unavailable",
        )
        eglConfig = checkNotNull(configs[0])
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        requireEgl(eglContext != EGL14.EGL_NO_CONTEXT, "EGL context failed")
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            outputTexture,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        requireEgl(eglSurface != EGL14.EGL_NO_SURFACE, "EGL window surface failed")
        requireEgl(
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext),
            "EGL makeCurrent failed",
        )

        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureMatrixLocation = GLES20.glGetUniformLocation(program, "uTextureMatrix")
        textureLocation = GLES20.glGetUniformLocation(program, "uTexture")
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        installMesh(calibration)
        createCameraInput()
    }

    private fun createCameraInput() {
        val input = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(DEWARP_SOURCE_WIDTH, DEWARP_SOURCE_HEIGHT)
            setOnFrameAvailableListener(
                { texture ->
                    if (texture !== cameraTexture || released.get()) return@setOnFrameAvailableListener
                    signalFrame()
                },
                checkNotNull(handler),
            )
        }
        cameraTexture = input
        cameraSurface = Surface(input)
    }

    private fun signalFrame() {
        pendingFrameSignals++
        if (renderQueued) return
        renderQueued = true
        if (handler?.post(::renderLatestFrame) != true) renderQueued = false
    }

    private fun renderLatestFrame() {
        renderQueued = false
        if (released.get()) return
        pendingFrameSignals = 0
        runCatching(::renderFrame).onFailure { error ->
            cameraTexture?.setOnFrameAvailableListener(null)
            if (runtimeFailureReported.compareAndSet(false, true)) onFatalError(error)
        }
        if (!released.get() && pendingFrameSignals > 0) signalFrame()
    }

    private fun installMesh(value: BlindSpotCalibration) {
        var rawFallback = false
        val mesh = try {
            buildBlindSpotDewarpMesh(value, outputWidth, outputHeight)
        } catch (error: IllegalArgumentException) {
            rawFallback = true
            onFallback(error.message ?: "fisheye calibrated domain unavailable")
            buildBlindSpotDewarpMesh(
                value.copy(dewarpEnabled = false),
                outputWidth,
                outputHeight,
            )
        }
        vertices = mesh.vertices.toFloatBuffer()
        indices = mesh.indices.toShortBuffer()
        indexCount = mesh.indices.size
        dewarpActive = value.dewarpEnabled && !rawFallback
        onMappingApplied(dewarpActive)
    }

    private fun scheduleLatestUpdate() {
        val activeHandler = handler ?: return
        if (mappingUpdatePosted.compareAndSet(false, true)) {
            if (!activeHandler.post(::applyLatestUpdate)) mappingUpdatePosted.set(false)
        }
    }

    private fun applyLatestUpdate() {
        mappingUpdatePosted.set(false)
        if (released.get()) return
        val latest = pendingUpdate ?: return
        calibration = latest.calibration
        if (latest.width > 0) outputWidth = latest.width
        if (latest.height > 0) outputHeight = latest.height
        installMesh(latest.calibration)
        if (!released.get() && pendingUpdate !== latest) scheduleLatestUpdate()
    }

    private fun releaseBlockingAfterStartupFailure() {
        if (!released.compareAndSet(false, true)) return
        val activeHandler = handler
        handler = null
        if (activeHandler == null) {
            thread.quitSafely()
            return
        }
        val done = CountDownLatch(1)
        activeHandler.post {
            try {
                releaseGl()
            } finally {
                done.countDown()
            }
        }
        runCatching { done.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        thread.quitSafely()
    }

    private fun renderFrame() {
        val input = cameraTexture ?: return
        val drawVertices = vertices ?: return
        val drawIndices = indices ?: return
        requireEgl(
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext),
            "EGL makeCurrent failed",
        )
        input.updateTexImage()
        input.getTransformMatrix(textureMatrix)
        if (!renderingEnabled) return
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        drawVertices.position(0)
        GLES20.glVertexAttribPointer(
            positionLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            drawVertices,
        )
        drawVertices.position(2)
        GLES20.glVertexAttribPointer(
            texCoordLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            drawVertices,
        )
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        GLES20.glUniformMatrix4fv(textureMatrixLocation, 1, false, textureMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureLocation, 0)
        drawIndices.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            drawIndices,
        )
        val glError = GLES20.glGetError()
        check(glError == GLES20.GL_NO_ERROR) { "GLES draw failed: 0x${glError.toString(16)}" }
        requireEgl(EGL14.eglSwapBuffers(eglDisplay, eglSurface), "EGL swap failed")
    }

    private fun releaseGl() {
        cameraTexture?.setOnFrameAvailableListener(null)
        cameraSurface?.release()
        cameraSurface = null
        cameraTexture?.release()
        cameraTexture = null
        renderQueued = false
        pendingFrameSignals = 0
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        textureId = 0
        program = 0
        vertices = null
        indices = null
        indexCount = 0
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglConfig = null
    }

    companion object {
        fun start(
            outputTexture: SurfaceTexture,
            outputWidth: Int,
            outputHeight: Int,
            calibration: BlindSpotCalibration,
            onFallback: (String) -> Unit,
            onMappingApplied: (Boolean) -> Unit,
            onFatalError: (Throwable) -> Unit,
        ): BlindSpotGpuDewarpRenderer? {
            val renderer = BlindSpotGpuDewarpRenderer(
                outputTexture,
                outputWidth,
                outputHeight,
                calibration,
                onFallback,
                onMappingApplied,
                onFatalError,
            )
            return renderer.takeIf { it.start() }
        }

        private const val START_TIMEOUT_MS = 1_500L
        private const val RELEASE_TIMEOUT_MS = 1_500L
        private const val VERTEX_STRIDE_BYTES = 16

        private data class MappingUpdate(
            val calibration: BlindSpotCalibration,
            val width: Int,
            val height: Int,
        )
        private const val VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
                "attribute vec2 aTexCoord;\n" +
                "uniform mat4 uTextureMatrix;\n" +
                "varying vec2 vRawTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main(){\n" +
                " gl_Position=vec4(aPosition,0.0,1.0);\n" +
                " vRawTexCoord=aTexCoord;\n" +
                " vTexCoord=(uTextureMatrix*vec4(aTexCoord,0.0,1.0)).xy;\n" +
                "}\n"
        private const val FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "uniform samplerExternalOES uTexture;\n" +
                "varying vec2 vRawTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main(){\n" +
                " if(vRawTexCoord.x<0.0||vRawTexCoord.x>1.0" +
                "||vRawTexCoord.y<0.0||vRawTexCoord.y>1.0" +
                "||vTexCoord.x<0.0||vTexCoord.x>1.0" +
                "||vTexCoord.y<0.0||vTexCoord.y>1.0){" +
                "gl_FragColor=vec4(0.0,0.0,0.0,1.0);return;}\n" +
                " gl_FragColor=texture2D(uTexture,vTexCoord);\n" +
                "}\n"

        private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            if (status[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                error("GL program link failed: $error")
            }
            return program
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error("GL shader compile failed: $error")
            }
            return shader
        }

        private fun requireEgl(value: Boolean, message: String) {
            check(value) { "$message (0x${EGL14.eglGetError().toString(16)})" }
        }

        private fun FloatArray.toFloatBuffer(): FloatBuffer =
            ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(this@toFloatBuffer).position(0) }

        private fun ShortArray.toShortBuffer(): ShortBuffer =
            ByteBuffer.allocateDirect(size * Short.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply { put(this@toShortBuffer).position(0) }
    }
}
