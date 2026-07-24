package com.byd.wjx.electronicmirror

internal data class VehicleCameraSortProperty(
    val name: String,
    val value: String,
)

/** Resolves the local panorama camera mapping from the head unit property chain. */
internal object VehicleCameraPropertyResolver {
    const val PANORAMA_CAMERA_TAG = "pano_h"

    val cameraSortPropertyNames = listOf(
        "vehicle.config.cam_sort",
        "vendor.vehicle.config.cam_sort",
    )

    fun readAll(propertyReader: (String) -> String): List<VehicleCameraSortProperty> =
        cameraSortPropertyNames.map { name ->
            VehicleCameraSortProperty(name, propertyReader(name).trim())
        }

    fun resolveForTag(
        tag: String,
        propertyReader: (String) -> String,
    ): VehicleCameraSortProperty? {
        val values = readAll(propertyReader)
        return values.firstOrNull { cameraIdFromSort(it.value, tag) != null }
            ?: values.firstOrNull { it.value.isNotBlank() }
    }

    fun cameraIdFromSort(raw: String, tag: String): Int? =
        raw.split(';', ',')
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { entry ->
                val separator = entry.indexOfAny(charArrayOf(':', '='))
                if (separator <= 0) null
                else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
            }
            .firstOrNull { (name, _) -> name.equals(tag, ignoreCase = true) }
            ?.second
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }
}
