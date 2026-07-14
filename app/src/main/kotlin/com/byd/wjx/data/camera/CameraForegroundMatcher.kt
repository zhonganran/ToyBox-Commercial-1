package com.byd.wjx.data.camera

object CameraForegroundMatcher {
    fun isCameraPackage(packageName: String?): Boolean {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank()) return false
        return pkg in CAMERA_PACKAGES ||
            CAMERA_PACKAGE_KEYWORDS.any { pkg.contains(it, ignoreCase = true) }
    }

    fun isCameraWindow(packageName: String?, className: String?): Boolean =
        isCameraPackage(packageName) || isCameraClass(className)

    fun isCameraClass(className: String?): Boolean {
        val cls = className?.trim().orEmpty()
        if (cls.isBlank()) return false
        return CAMERA_CLASS_KEYWORDS.any { cls.contains(it, ignoreCase = true) }
    }

    private val CAMERA_PACKAGES = setOf(
        "com.byd.avc",
        "com.byd.camera",
        "com.byd.panorama",
        "com.byd.avm",
        "com.byd.surroundview",
        "com.byd.autovideo",
        "com.byd.parkingcamera",
    )

    private val CAMERA_PACKAGE_KEYWORDS = listOf(
        "byd.avc",
        "byd.camera",
        "byd.panorama",
        "byd.avm",
        "camera",
        "panorama",
        "surround",
        "aroundview",
        "rearview",
        "reverse",
        "parkingcamera",
    )

    private val CAMERA_CLASS_KEYWORDS = listOf(
        "Camera",
        "Panorama",
        "VehiclePanorama",
        "Surround",
        "AroundView",
        "Avm",
        "AVM",
        "Reverse",
        "RearView",
        "ParkingCamera",
    )
}
