package com.byd.wjx.electronicmirror

import android.annotation.SuppressLint
import android.content.Context
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
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import com.byd.wjx.log.WjxFileLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred

/**
 * One panorama producer shared by every visible electronic-mirror window.
 *
 * AutoTark keeps one camera source and fans the same frame out to every registered TextureView.
 * Doing the same here lets the left, right and streaming-rear windows coexist without opening the
 * BYD camera three times or switching its Surface back and forth. Frames never leave the GPU.
 */
internal class ElectronicMirrorFrameHub(
    bufferWidth: Int,
    bufferHeight: Int,
    private val onRendererFailed: (Throwable) -> Unit,
) {
    val outputWidth: Int = bufferWidth.coerceAtLeast(1)
    val outputHeight: Int = bufferHeight.coerceAtLeast(1)
    val inputReady = CompletableDeferred<Surface?>()
    val frameCount = AtomicLong(0L)

    @Volatile
    var inputSurface: Surface? = null
        private set

    @Volatile
    var lastFrameAtMs: Long = 0L
        private set

    @Volatile
    private var released = false

    private val outputSequence = AtomicLong(1L)
    private val renderer = SharedOpaqueFrameRenderer(
        width = outputWidth,
        height = outputHeight,
        onInputSurfaceReady = { surface ->
            if (released) {
                surface.release()
            } else {
                inputSurface = surface
                if (!inputReady.isCompleted) inputReady.complete(surface)
            }
        },
        onFrameRendered = {
            frameCount.incrementAndGet()
            lastFrameAtMs = SystemClock.elapsedRealtime()
        },
        onRendererFailed = { error ->
            if (!inputReady.isCompleted) inputReady.complete(null)
            if (!released) onRendererFailed(error)
        },
    )

    init {
        renderer.start()
    }

    fun nextOutputId(): Long = outputSequence.getAndIncrement()

    fun attachOutput(
        outputId: Long,
        texture: SurfaceTexture,
        onAttached: (Boolean) -> Unit,
        onOutputFailed: (Throwable) -> Unit,
    ) {
        if (released) {
            onAttached(false)
            return
        }
        texture.setDefaultBufferSize(outputWidth, outputHeight)
        renderer.attachOutput(outputId, texture, onAttached, onOutputFailed)
    }

    fun detachOutput(outputId: Long) {
        renderer.detachOutput(outputId)
    }

    fun release() {
        if (released) return
        released = true
        if (!inputReady.isCompleted) inputReady.complete(null)
        renderer.releaseBlocking()
        inputSurface = null
    }

    private class SharedOpaqueFrameRenderer(
        private val width: Int,
        private val height: Int,
        private val onInputSurfaceReady: (Surface) -> Unit,
        private val onFrameRendered: () -> Unit,
        private val onRendererFailed: (Throwable) -> Unit,
    ) {
        private val thread = HandlerThread("ElectronicMirrorFrames")
        private lateinit var handler: Handler

        @Volatile
        private var released = false

        private var terminalFailureReported = false
        private var consecutiveInputFailures = 0
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var idleSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var eglConfig: EGLConfig? = null
        private var inputTexture: SurfaceTexture? = null
        private var inputSurface: Surface? = null
        private var externalTextureId = 0
        private var program = 0
        private var positionLocation = -1
        private var textureCoordinateLocation = -1
        private var textureMatrixLocation = -1
        private var samplerLocation = -1
        private val textureMatrix = FloatArray(16)
        private val outputs = linkedMapOf<Long, OutputTarget>()
        private val positionBuffer = floatBuffer(
            floatArrayOf(
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f,
            ),
        )
        private val textureCoordinateBuffer = floatBuffer(
            floatArrayOf(
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f,
            ),
        )

        fun start() {
            thread.start()
            handler = Handler(thread.looper)
            handler.post {
                runCatching(::initialize)
                    .onFailure { error ->
                        WjxFileLogger.e(TAG, "shared renderer init failed: ${error.message}", error)
                        cleanup()
                        reportTerminalFailure(error)
                    }
            }
        }

        fun attachOutput(
            outputId: Long,
            texture: SurfaceTexture,
            onAttached: (Boolean) -> Unit,
            onOutputFailed: (Throwable) -> Unit,
        ) {
            if (released || !::handler.isInitialized) {
                onAttached(false)
                return
            }
            handler.post {
                if (released || terminalFailureReported) {
                    onAttached(false)
                    return@post
                }
                runCatching {
                    removeOutput(outputId)
                    val config = checkNotNull(eglConfig) { "EGL config unavailable" }
                    val surface = Surface(texture)
                    val eglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay,
                        config,
                        surface,
                        intArrayOf(EGL14.EGL_NONE),
                        0,
                    )
                    if (eglSurface == EGL14.EGL_NO_SURFACE) {
                        surface.release()
                        error("cannot create output EGL surface")
                    }
                    outputs[outputId] = OutputTarget(
                        surface = surface,
                        eglSurface = eglSurface,
                        onFailure = onOutputFailed,
                    )
                    WjxFileLogger.i(TAG, "output attached id=$outputId count=${outputs.size}")
                }.onSuccess {
                    onAttached(true)
                }.onFailure { error ->
                    WjxFileLogger.w(TAG, "output attach failed id=$outputId: ${error.message}")
                    onAttached(false)
                    onOutputFailed(error)
                }
            }
        }

        fun detachOutput(outputId: Long) {
            if (!::handler.isInitialized) return
            handler.post { removeOutput(outputId) }
        }

        fun releaseBlocking() {
            if (released) return
            released = true
            if (!::handler.isInitialized) {
                thread.quitSafely()
                return
            }
            if (Looper.myLooper() == handler.looper) {
                cleanup()
                thread.quitSafely()
                return
            }
            val completed = CountDownLatch(1)
            if (handler.post {
                    cleanup()
                    completed.countDown()
                    thread.quitSafely()
                }
            ) {
                completed.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        }

        private fun initialize() {
            check(!released) { "renderer already released" }
            initializeEgl()
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
            textureCoordinateLocation = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
            textureMatrixLocation = GLES20.glGetUniformLocation(program, "uTextureMatrix")
            samplerLocation = GLES20.glGetUniformLocation(program, "uCameraTexture")
            check(
                positionLocation >= 0 &&
                    textureCoordinateLocation >= 0 &&
                    textureMatrixLocation >= 0 &&
                    samplerLocation >= 0,
            ) { "required GLES locations unavailable" }

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            externalTextureId = textures[0]
            check(externalTextureId != 0) { "cannot allocate external camera texture" }
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
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

            val cameraTexture = SurfaceTexture(externalTextureId).apply {
                setDefaultBufferSize(width, height)
                setOnFrameAvailableListener({ renderFrame() }, handler)
            }
            inputTexture = cameraTexture
            inputSurface = Surface(cameraTexture).also(onInputSurfaceReady)
            WjxFileLogger.i(TAG, "shared renderer ready buffer=${width}x$height")
        }

        private fun initializeEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "EGL display unavailable" }
            val versions = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) {
                "EGL initialization failed"
            }
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(
                EGL14.eglChooseConfig(
                    eglDisplay,
                    attributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    count,
                    0,
                ) && count[0] > 0,
            ) { "EGL config unavailable" }
            val config = checkNotNull(configs[0]) { "EGL config unavailable" }
            eglConfig = config
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "EGL context creation failed" }
            idleSurface = EGL14.eglCreatePbufferSurface(
                eglDisplay,
                config,
                intArrayOf(
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE,
                ),
                0,
            )
            check(idleSurface != EGL14.EGL_NO_SURFACE) { "EGL pbuffer creation failed" }
            check(EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)) {
                "EGL makeCurrent failed"
            }
            EGL14.eglSwapInterval(eglDisplay, 0)
        }

        private fun renderFrame() {
            if (released || terminalFailureReported || eglDisplay == EGL14.EGL_NO_DISPLAY) return
            val cameraTexture = inputTexture ?: return
            runCatching {
                check(EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)) {
                    "EGL input makeCurrent failed"
                }
                cameraTexture.updateTexImage()
                cameraTexture.getTransformMatrix(textureMatrix)
                consecutiveInputFailures = 0

                var rendered = 0
                val failedOutputs = mutableListOf<Pair<Long, Throwable>>()
                outputs.forEach { (outputId, output) ->
                    runCatching { drawTo(output.eglSurface) }
                        .onSuccess { rendered++ }
                        .onFailure { failedOutputs += outputId to it }
                }
                failedOutputs.forEach { (outputId, error) ->
                    val failed = outputs[outputId]
                    WjxFileLogger.w(TAG, "output render failed id=$outputId: ${error.message}")
                    removeOutput(outputId)
                    failed?.onFailure?.invoke(error)
                }
                if (rendered > 0) onFrameRendered()
            }.onFailure { error ->
                consecutiveInputFailures++
                if (consecutiveInputFailures <= MAX_REPORTED_INPUT_FAILURES) {
                    WjxFileLogger.w(
                        TAG,
                        "shared input frame failed #$consecutiveInputFailures: ${error.message}",
                    )
                }
                if (consecutiveInputFailures >= MAX_CONSECUTIVE_INPUT_FAILURES) {
                    reportTerminalFailure(error)
                }
            }
        }

        private fun drawTo(targetSurface: EGLSurface) {
            check(EGL14.eglMakeCurrent(eglDisplay, targetSurface, targetSurface, eglContext)) {
                "EGL output makeCurrent failed"
            }
            GLES20.glViewport(0, 0, width, height)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glColorMask(true, true, true, true)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            positionBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionLocation)
            GLES20.glVertexAttribPointer(
                positionLocation,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                positionBuffer,
            )
            textureCoordinateBuffer.position(0)
            GLES20.glEnableVertexAttribArray(textureCoordinateLocation)
            GLES20.glVertexAttribPointer(
                textureCoordinateLocation,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                textureCoordinateBuffer,
            )
            GLES20.glUniformMatrix4fv(textureMatrixLocation, 1, false, textureMatrix, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
            GLES20.glUniform1i(samplerLocation, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionLocation)
            GLES20.glDisableVertexAttribArray(textureCoordinateLocation)

            val drawError = GLES20.glGetError()
            check(drawError == GLES20.GL_NO_ERROR) {
                "GLES draw error=0x${drawError.toString(16)}"
            }
            check(EGL14.eglSwapBuffers(eglDisplay, targetSurface)) { "EGL swap failed" }
        }

        private fun removeOutput(outputId: Long) {
            val output = outputs.remove(outputId) ?: return
            if (eglDisplay != EGL14.EGL_NO_DISPLAY && output.eglSurface != EGL14.EGL_NO_SURFACE) {
                if (idleSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)
                }
                EGL14.eglDestroySurface(eglDisplay, output.eglSurface)
            }
            output.surface.release()
            WjxFileLogger.i(TAG, "output detached id=$outputId count=${outputs.size}")
        }

        private fun reportTerminalFailure(error: Throwable) {
            if (terminalFailureReported || released) return
            terminalFailureReported = true
            onRendererFailed(error)
        }

        private fun cleanup() {
            inputTexture?.setOnFrameAvailableListener(null)
            if (
                eglDisplay != EGL14.EGL_NO_DISPLAY &&
                idleSurface != EGL14.EGL_NO_SURFACE &&
                eglContext != EGL14.EGL_NO_CONTEXT
            ) {
                EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)
            }
            outputs.keys.toList().forEach(::removeOutput)
            inputSurface?.release()
            inputSurface = null
            inputTexture?.release()
            inputTexture = null
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                if (program != 0) GLES20.glDeleteProgram(program)
                if (externalTextureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(externalTextureId), 0)
                }
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
                if (idleSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, idleSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            idleSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglConfig = null
            program = 0
            externalTextureId = 0
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            return GLES20.glCreateProgram().also { created ->
                check(created != 0) { "cannot create GLES program" }
                GLES20.glAttachShader(created, vertexShader)
                GLES20.glAttachShader(created, fragmentShader)
                GLES20.glLinkProgram(created)
                val linked = IntArray(1)
                GLES20.glGetProgramiv(created, GLES20.GL_LINK_STATUS, linked, 0)
                val log = GLES20.glGetProgramInfoLog(created)
                GLES20.glDeleteShader(vertexShader)
                GLES20.glDeleteShader(fragmentShader)
                check(linked[0] == GLES20.GL_TRUE) { "GLES link failed: $log" }
            }
        }

        private fun compileShader(type: Int, source: String): Int =
            GLES20.glCreateShader(type).also { shader ->
                check(shader != 0) { "cannot create GLES shader" }
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                val log = GLES20.glGetShaderInfoLog(shader)
                check(compiled[0] == GLES20.GL_TRUE) { "GLES compile failed: $log" }
            }

        private data class OutputTarget(
            val surface: Surface,
            val eglSurface: EGLSurface,
            val onFailure: (Throwable) -> Unit,
        )

        companion object {
            private const val RELEASE_TIMEOUT_MS = 1_000L
            private const val MAX_REPORTED_INPUT_FAILURES = 3
            private const val MAX_CONSECUTIVE_INPUT_FAILURES = 3

            private val VERTEX_SHADER = """
                attribute vec4 aPosition;
                attribute vec2 aTextureCoordinate;
                uniform mat4 uTextureMatrix;
                varying vec2 vCameraUv;

                void main() {
                    gl_Position = aPosition;
                    vCameraUv = (uTextureMatrix * vec4(aTextureCoordinate, 0.0, 1.0)).xy;
                }
            """.trimIndent()

            private val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;

                uniform samplerExternalOES uCameraTexture;
                varying vec2 vCameraUv;

                void main() {
                    vec3 cameraRgb = texture2D(uCameraTexture, vCameraUv).rgb;
                    gl_FragColor = vec4(cameraRgb, 1.0);
                }
            """.trimIndent()

            private fun floatBuffer(values: FloatArray): FloatBuffer =
                ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(values)
                        position(0)
                    }
        }
    }

    companion object {
        private const val TAG = "ElectronicMirrorFrames"
    }
}

/** One opaque TextureView consumer attached to [ElectronicMirrorFrameHub]. */
@SuppressLint("ViewConstructor")
internal class ElectronicMirrorOpaqueTextureView(
    context: Context,
    private val frameHub: ElectronicMirrorFrameHub,
    private val onOutputReady: (Boolean) -> Unit,
    private val onOutputDestroyed: () -> Unit,
    private val onOutputFailed: (Throwable) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private val outputId = frameHub.nextOutputId()

    @Volatile
    private var released = false

    init {
        // This is the important AutoTark behaviour: TextureView itself is declared opaque. The
        // compositor must never blend the navigation layer below with a real camera buffer.
        isOpaque = true
        alpha = 1f
        surfaceTextureListener = this
    }

    fun releaseRenderer() {
        if (released) return
        released = true
        frameHub.detachOutput(outputId)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        frameHub.attachOutput(
            outputId = outputId,
            texture = texture,
            onAttached = onOutputReady,
            onOutputFailed = onOutputFailed,
        )
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        texture.setDefaultBufferSize(frameHub.outputWidth, frameHub.outputHeight)
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releaseRenderer()
        onOutputDestroyed()
        return true
    }
}
