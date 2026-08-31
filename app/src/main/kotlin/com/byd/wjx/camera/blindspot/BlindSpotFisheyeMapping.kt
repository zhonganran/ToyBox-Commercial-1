package com.byd.wjx.camera.blindspot

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

internal data class BlindSpotDewarpMesh(
    val vertices: FloatArray,
    val indices: ShortArray,
)

internal fun buildBlindSpotDewarpMesh(
    calibration: BlindSpotCalibration,
    outputWidth: Int,
    outputHeight: Int,
): BlindSpotDewarpMesh {
    require(outputWidth > 0 && outputHeight > 0) { "Output size must be positive" }
    if (!calibration.dewarpEnabled) return identityDewarpMesh()
    val basis = fisheyeBasis(
        calibration.dewarpCenterXPct / 100.0,
        calibration.dewarpCenterYPct / 100.0,
    )
    val vertexCount = DEWARP_MESH_COLUMNS * DEWARP_MESH_ROWS
    val vertices = FloatArray(vertexCount * 4)
    val valid = BooleanArray(vertexCount)
    var cursor = 0
    for (row in 0 until DEWARP_MESH_ROWS) {
        val outputY = row.toDouble() / (DEWARP_MESH_ROWS - 1)
        for (column in 0 until DEWARP_MESH_COLUMNS) {
            val outputX = column.toDouble() / (DEWARP_MESH_COLUMNS - 1)
            val source = mapFisheyeOutputToSource(
                calibration,
                outputWidth,
                outputHeight,
                basis,
                outputX,
                outputY,
            )
            vertices[cursor++] = (outputX * 2.0 - 1.0).toFloat()
            vertices[cursor++] = (1.0 - outputY * 2.0).toFloat()
            vertices[cursor++] = (source[0] / DEWARP_SOURCE_WIDTH).toFloat()
            vertices[cursor++] = (1.0 - source[1] / DEWARP_SOURCE_HEIGHT).toFloat()
            valid[row * DEWARP_MESH_COLUMNS + column] = !invalidFisheyeRay(source)
        }
    }

    val allIndices = ShortArray((DEWARP_MESH_COLUMNS - 1) * (DEWARP_MESH_ROWS - 1) * 6)
    cursor = 0
    for (row in 0 until DEWARP_MESH_ROWS - 1) {
        for (column in 0 until DEWARP_MESH_COLUMNS - 1) {
            val topLeft = row * DEWARP_MESH_COLUMNS + column
            val topRight = topLeft + 1
            val bottomLeft = topLeft + DEWARP_MESH_COLUMNS
            val bottomRight = bottomLeft + 1
            if (valid[topLeft] && valid[bottomLeft] && valid[topRight]) {
                allIndices[cursor++] = topLeft.toShort()
                allIndices[cursor++] = bottomLeft.toShort()
                allIndices[cursor++] = topRight.toShort()
            }
            if (valid[topRight] && valid[bottomLeft] && valid[bottomRight]) {
                allIndices[cursor++] = topRight.toShort()
                allIndices[cursor++] = bottomLeft.toShort()
                allIndices[cursor++] = bottomRight.toShort()
            }
        }
    }
    return BlindSpotDewarpMesh(vertices, allIndices.copyOf(cursor))
}

internal fun mapFisheyeOutputToSource(
    calibration: BlindSpotCalibration,
    outputWidth: Int,
    outputHeight: Int,
    outputX: Double,
    outputY: Double,
): DoubleArray = mapFisheyeOutputToSource(
    calibration,
    outputWidth,
    outputHeight,
    fisheyeBasis(
        calibration.dewarpCenterXPct / 100.0,
        calibration.dewarpCenterYPct / 100.0,
    ),
    outputX,
    outputY,
)

private fun mapFisheyeOutputToSource(
    calibration: BlindSpotCalibration,
    outputWidth: Int,
    outputHeight: Int,
    basis: FisheyeBasis,
    outputX: Double,
    outputY: Double,
): DoubleArray {
    val fovRadians = Math.toRadians(calibration.dewarpFovDegrees.toDouble())
    return when (calibration.dewarpProjection) {
        BlindSpotDewarpProjection.CYLINDRICAL -> {
            val yaw = (outputX - 0.5) * fovRadians
            val cylinderY = (outputY - 0.5) * outputHeight / outputWidth * fovRadians
            mapLocalFisheyeRay(basis, sin(yaw), cylinderY, cos(yaw))
        }
        BlindSpotDewarpProjection.RECTILINEAR -> {
            val outputFocal = outputWidth / (2.0 * tan(fovRadians / 2.0))
            val x = (outputX * outputWidth - outputWidth / 2.0) / outputFocal
            val y = (outputY * outputHeight - outputHeight / 2.0) / outputFocal
            mapLocalFisheyeRay(basis, x, y, 1.0)
        }
    }
}

private fun mapLocalFisheyeRay(
    basis: FisheyeBasis,
    x: Double,
    y: Double,
    z: Double,
): DoubleArray = mapFisheyeRay(
    basis.rightX * x + basis.downX * y + basis.forwardX * z,
    basis.rightY * x + basis.downY * y + basis.forwardY * z,
    basis.rightZ * x + basis.downZ * y + basis.forwardZ * z,
)

private fun mapFisheyeRay(x: Double, y: Double, z: Double): DoubleArray {
    val radius = hypot(x, y)
    var scale = 1.0
    if (radius > 1.0e-12) {
        val theta = atan2(radius, z)
        if (theta > MAX_INVERTIBLE_THETA) {
            return doubleArrayOf(-DEWARP_SOURCE_WIDTH.toDouble(), -DEWARP_SOURCE_HEIGHT.toDouble())
        }
        val theta2 = theta * theta
        val theta4 = theta2 * theta2
        val theta6 = theta4 * theta2
        val theta8 = theta4 * theta4
        val distorted = theta * (
            1.0 + FISHEYE_K1 * theta2 + FISHEYE_K2 * theta4 +
                FISHEYE_K3 * theta6 + FISHEYE_K4 * theta8
            )
        scale = distorted / radius
    }
    return doubleArrayOf(
        FISHEYE_FX * x * scale + FISHEYE_CX,
        FISHEYE_FY * y * scale + FISHEYE_CY,
    )
}

private fun fisheyeBasis(normalizedX: Double, normalizedY: Double): FisheyeBasis {
    val dx = (normalizedX * DEWARP_SOURCE_WIDTH - FISHEYE_CX) / FISHEYE_FX
    val dy = (normalizedY * DEWARP_SOURCE_HEIGHT - FISHEYE_CY) / FISHEYE_FY
    val distortedRadius = hypot(dx, dy)
    if (distortedRadius <= 1.0e-12) return FisheyeBasis.identity()
    if (distortedRadius > distortedTheta(MAX_INVERTIBLE_THETA)) {
        throw IllegalArgumentException("Dewarp optical center is outside calibrated fisheye domain")
    }
    var low = 0.0
    var high = MAX_INVERTIBLE_THETA
    repeat(INVERSE_ITERATIONS) {
        val middle = (low + high) / 2.0
        if (distortedTheta(middle) < distortedRadius) low = middle else high = middle
    }
    val theta = (low + high) / 2.0
    val radialScale = sin(theta) / distortedRadius
    val forwardX = dx * radialScale
    val forwardY = dy * radialScale
    val forwardZ = cos(theta)
    val rightLength = hypot(forwardX, forwardZ)
    val rightX = if (rightLength <= 1.0e-12) 1.0 else forwardZ / rightLength
    val rightZ = if (rightLength <= 1.0e-12) 0.0 else -forwardX / rightLength
    return FisheyeBasis(
        rightX = rightX,
        rightY = 0.0,
        rightZ = rightZ,
        downX = forwardY * rightZ,
        downY = forwardZ * rightX - forwardX * rightZ,
        downZ = -forwardY * rightX,
        forwardX = forwardX,
        forwardY = forwardY,
        forwardZ = forwardZ,
    )
}

private fun distortedTheta(theta: Double): Double {
    val theta2 = theta * theta
    val theta4 = theta2 * theta2
    val theta6 = theta4 * theta2
    val theta8 = theta4 * theta4
    return theta * (
        1.0 + FISHEYE_K1 * theta2 + FISHEYE_K2 * theta4 +
            FISHEYE_K3 * theta6 + FISHEYE_K4 * theta8
        )
}

private fun invalidFisheyeRay(source: DoubleArray): Boolean =
    source[0] == -DEWARP_SOURCE_WIDTH.toDouble() && source[1] == -DEWARP_SOURCE_HEIGHT.toDouble()

private fun identityDewarpMesh() = BlindSpotDewarpMesh(
    vertices = floatArrayOf(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f,
    ),
    indices = shortArrayOf(0, 1, 2, 2, 1, 3),
)

private data class FisheyeBasis(
    val rightX: Double,
    val rightY: Double,
    val rightZ: Double,
    val downX: Double,
    val downY: Double,
    val downZ: Double,
    val forwardX: Double,
    val forwardY: Double,
    val forwardZ: Double,
) {
    companion object {
        fun identity() = FisheyeBasis(
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0,
        )
    }
}

internal const val DEWARP_SOURCE_WIDTH = 1920
internal const val DEWARP_SOURCE_HEIGHT = 1300
private const val DEWARP_MESH_COLUMNS = 97
private const val DEWARP_MESH_ROWS = 65
private const val MAX_INVERTIBLE_THETA = 2.00078250962346
private const val INVERSE_ITERATIONS = 48
private const val FISHEYE_FX = 433.0
private const val FISHEYE_FY = 433.0
private const val FISHEYE_CX = 960.0
private const val FISHEYE_CY = 650.0
private const val FISHEYE_K1 = 0.252969
private const val FISHEYE_K2 = -0.096487
private const val FISHEYE_K3 = 0.023289
private const val FISHEYE_K4 = -0.002927
