package com.byd.wjx.vehicle

import android.annotation.SuppressLint
import com.byd.wjx.activation.ActivationManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Vehicle identity read from the car head unit. No compatibility or camera data belongs here. */
data class VehicleInfo(
    val vin: String,
    val vinYear: Int?,
    val displayName: String,
    val modelVariant: String,
    val carSeries: String,
    val vehicleType: String,
    val controllerVersion: String,
    val controllerSeries: Int,
) {
    val displayLabel: String?
        get() = composeDisplayLabel(vinYear, displayName)
}

@Singleton
class VehicleIdentityReader @Inject constructor(
    private val activationManager: ActivationManager,
) {
    fun snapshot(): VehicleInfo {
        val vin = activationManager.currentSnapshot().deviceCode.trim().uppercase(Locale.ROOT)
        return VehicleIdentityResolver.read().copy(
            vin = vin,
            vinYear = VinYearParser.parse(vin),
        )
    }
}

object VehicleIdentityResolver {
    fun read(
        propertyReader: (String) -> String = VehicleSystemProperties::read,
    ): VehicleInfo {
        val controllerVersion = readControllerVersion(propertyReader)
        return VehicleInfo(
            vin = "",
            vinYear = null,
            displayName = readDisplayName(propertyReader),
            modelVariant = propertyReader("persist.sys.model_variant.model").trim(),
            carSeries = propertyReader("ro.build.car.series").trim(),
            vehicleType = propertyReader("ro.vehicle.type").trim(),
            controllerVersion = controllerVersion,
            controllerSeries = CarProfileDetector.parseControllerSeries(controllerVersion) ?: 0,
        )
    }

    fun readDisplayName(
        propertyReader: (String) -> String = VehicleSystemProperties::read,
    ): String = propertyReader("persist.sys.byd.default_name").trim()

    fun readControllerVersion(
        propertyReader: (String) -> String = VehicleSystemProperties::read,
    ): String = sequenceOf(
        "apps.setting.product.outswver",
        "ro.build.version.incremental",
        "ro.vendor.build.version.incremental",
        "ro.build.display.id",
    ).asSequence()
        .map(propertyReader)
        .map(String::trim)
        .firstOrNull { CarProfileDetector.parseControllerSeries(it) != null }
        .orEmpty()

}

object VinYearParser {
    private val yearCodes = mapOf(
        'A' to 1980, 'B' to 1981, 'C' to 1982, 'D' to 1983, 'E' to 1984,
        'F' to 1985, 'G' to 1986, 'H' to 1987, 'J' to 1988, 'K' to 1989,
        'L' to 1990, 'M' to 1991, 'N' to 1992, 'P' to 1993, 'R' to 1994,
        'S' to 1995, 'T' to 1996, 'V' to 1997, 'W' to 1998, 'X' to 1999,
        'Y' to 2000, '1' to 2001, '2' to 2002, '3' to 2003, '4' to 2004,
        '5' to 2005, '6' to 2006, '7' to 2007, '8' to 2008, '9' to 2009,
    )

    fun parse(vin: String): Int? {
        val normalized = vin.trim().uppercase(Locale.ROOT)
        if (
            normalized.length != 17 ||
            normalized.any { it !in '0'..'9' && it !in 'A'..'Z' } ||
            normalized.any { it in "IOQ" }
        ) {
            return null
        }
        val base = yearCodes[normalized[9]] ?: return null
        val upperBound = Calendar.getInstance().get(Calendar.YEAR) + 1
        return generateSequence(base) { it + 30 }
            .takeWhile { it <= upperBound }
            .lastOrNull()
    }
}

fun composeDisplayLabel(vinYear: Int?, displayName: String): String? {
    val name = displayName.trim()
    if (name.isBlank()) return null
    if (vinYear == null) return name
    val yearPrefix = "${vinYear}款"
    return if (name.startsWith(yearPrefix)) name else "$yearPrefix $name"
}

fun retainKnownVehicleInfo(previous: VehicleInfo?, current: VehicleInfo): VehicleInfo {
    if (previous == null) return current
    val sameVehicle = current.vin.isBlank() || previous.vin.isBlank() || current.vin == previous.vin
    if (!sameVehicle) return current
    return current.copy(
        vin = current.vin.ifBlank { previous.vin },
        vinYear = current.vinYear ?: previous.vinYear,
        displayName = current.displayName.ifBlank { previous.displayName },
        modelVariant = current.modelVariant.ifBlank { previous.modelVariant },
        carSeries = current.carSeries.ifBlank { previous.carSeries },
        vehicleType = current.vehicleType.ifBlank { previous.vehicleType },
        controllerVersion = current.controllerVersion.ifBlank { previous.controllerVersion },
        controllerSeries = current.controllerSeries.takeIf { it > 0 } ?: previous.controllerSeries,
    )
}

object VehicleSystemProperties {
    @SuppressLint("PrivateApi")
    fun read(name: String): String {
        val direct = runCatching {
            val type = Class.forName("android.os.SystemProperties")
            type.getMethod("get", String::class.java).invoke(null, name) as? String
        }.getOrNull().orEmpty()
        if (direct.isNotBlank()) return direct

        return runCatching {
            val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
            val line = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
            process.waitFor()
            line.orEmpty()
        }.getOrDefault("")
    }
}
