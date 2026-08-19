package com.byd.wjx.ui.creative

import com.byd.wjx.vehicle.VehicleInfo
import org.json.JSONObject
import java.util.Locale

data class CreativeVehicle(
    val id: String,
    val brand: String,
    val name: String,
    val aliases: List<String>,
    val modelUrl: String,
    val size: Long,
    val version: Int,
    val sha256: String,
) {
    val displayName: String
        get() = "$brand $name".trim()
}

data class CreativeWrap(
    val id: String,
    val name: String,
    val textureUrl: String,
    val previewColor: String,
    val size: Long,
    val version: Int,
    val sha256: String,
)

data class CreativeWorkshopCatalog(
    val schemaVersion: Int,
    val viewerUrl: String,
    val vehicles: List<CreativeVehicle>,
    val wraps: List<CreativeWrap>,
) {
    companion object {
        fun parse(json: String): CreativeWorkshopCatalog {
            val root = JSONObject(json)
            val vehiclesJson = root.getJSONArray("vehicles")
            val vehicles = buildList {
                for (index in 0 until vehiclesJson.length()) {
                    val item = vehiclesJson.getJSONObject(index)
                    val aliasesJson = item.optJSONArray("aliases")
                    val aliases = buildList {
                        if (aliasesJson != null) {
                            for (aliasIndex in 0 until aliasesJson.length()) {
                                aliasesJson.optString(aliasIndex)
                                    .trim()
                                    .takeIf(String::isNotEmpty)
                                    ?.let(::add)
                            }
                        }
                    }
                    add(
                        CreativeVehicle(
                            id = item.getString("id").trim(),
                            brand = item.getString("brand").trim(),
                            name = item.getString("name").trim(),
                            aliases = aliases,
                            modelUrl = item.getString("modelUrl").trim(),
                            size = item.optLong("size"),
                            version = item.optInt("version", 1),
                            sha256 = item.optString("sha256").trim(),
                        ),
                    )
                }
            }
            val wrapsJson = root.optJSONArray("wraps")
            val wraps = buildList {
                if (wrapsJson != null) {
                    for (index in 0 until wrapsJson.length()) {
                        val item = wrapsJson.getJSONObject(index)
                        add(
                            CreativeWrap(
                                id = item.getString("id").trim(),
                                name = item.getString("name").trim(),
                                textureUrl = item.getString("textureUrl").trim(),
                                previewColor = item.optString("previewColor", "#78909C").trim(),
                                size = item.optLong("size"),
                                version = item.optInt("version", 1),
                                sha256 = item.optString("sha256").trim(),
                            ),
                        )
                    }
                }
            }
            return CreativeWorkshopCatalog(
                schemaVersion = root.optInt("schemaVersion", 1),
                viewerUrl = root.getString("viewerUrl").trim(),
                vehicles = vehicles,
                wraps = wraps,
            ).also { catalog ->
                require(catalog.viewerUrl.startsWith("https://")) { "查看器地址必须使用 HTTPS" }
                require(catalog.vehicles.isNotEmpty()) { "车型目录为空" }
                require(catalog.vehicles.map { it.id }.distinct().size == catalog.vehicles.size) {
                    "车型目录包含重复 ID"
                }
                require(catalog.vehicles.all { it.modelUrl.startsWith("https://") }) {
                    "车型资源地址必须使用 HTTPS"
                }
            }
        }
    }
}

object CreativeVehicleMatcher {
    fun findBestMatch(
        vehicleInfo: VehicleInfo,
        vehicles: List<CreativeVehicle>,
    ): CreativeVehicle? {
        val identityValues = listOf(
            vehicleInfo.displayName,
            vehicleInfo.modelVariant,
            vehicleInfo.carSeries,
            vehicleInfo.vehicleType,
        ).map(::normalize).filter { it.length >= 2 }
        if (identityValues.isEmpty()) return null

        return vehicles
            .map { vehicle -> vehicle to score(vehicle, identityValues) }
            .filter { (_, score) -> score >= MINIMUM_MATCH_SCORE }
            .maxWithOrNull(
                compareBy<Pair<CreativeVehicle, Int>> { it.second }
                    .thenBy { it.first.name.length },
            )
            ?.first
    }

    private fun score(vehicle: CreativeVehicle, identityValues: List<String>): Int {
        val candidateAliases = buildList {
            add(vehicle.name)
            add(vehicle.displayName)
            addAll(vehicle.aliases)
        }.map(::normalize).filter { it.isNotBlank() }.distinct()

        return identityValues.maxOf { identity ->
            candidateAliases.maxOfOrNull { alias ->
                when {
                    identity == alias -> 1_000 + alias.length
                    alias.length >= 3 && identity.contains(alias) -> 700 + alias.length
                    identity.length >= 4 && alias.contains(identity) -> 520 + identity.length
                    else -> 0
                }
            } ?: 0
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("dm-i", "dmi")
        .replace("dm i", "dmi")
        .replace(NON_IDENTITY_CHARACTERS, "")

    private val NON_IDENTITY_CHARACTERS = Regex("[^\\p{L}\\p{N}]")
    private const val MINIMUM_MATCH_SCORE = 700
}

data class CreativePaintOption(
    val id: String,
    val name: String,
    val color: String,
)

val CreativePaintOptions = listOf(
    CreativePaintOption("crimson", "赤霞红", "#C6424A"),
    CreativePaintOption("pearl", "珍珠白", "#E8ECEB"),
    CreativePaintOption("obsidian", "曜石黑", "#171B1D"),
    CreativePaintOption("aurora", "极光青", "#2A9D8F"),
    CreativePaintOption("ocean", "深海蓝", "#315C8C"),
    CreativePaintOption("violet", "星云紫", "#76558E"),
    CreativePaintOption("canyon", "峡谷金", "#B77B3D"),
    CreativePaintOption("sakura", "樱粉", "#D984A4"),
)
