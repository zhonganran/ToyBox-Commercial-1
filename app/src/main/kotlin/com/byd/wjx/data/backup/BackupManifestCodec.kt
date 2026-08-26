package com.byd.wjx.data.backup

import org.json.JSONArray
import org.json.JSONObject

internal object BackupManifestCodec {
    fun serialize(manifest: BackupManifest): String = JSONObject().apply {
        put("backupFormatVersion", manifest.backupFormatVersion)
        put("appVersionCode", manifest.appVersionCode)
        put("dbSchemaVersion", manifest.dbSchemaVersion)
        put("createdAt", manifest.createdAt)
        put("preferenceFiles", JSONArray(manifest.preferenceFiles))
    }.toString()

    fun deserialize(json: String): BackupManifest {
        val root = JSONObject(json)
        val preferenceFiles = root.getJSONArray("preferenceFiles").let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.getString(index))
            }
        }
        return BackupManifest(
            backupFormatVersion = root.getInt("backupFormatVersion"),
            appVersionCode = root.getInt("appVersionCode"),
            dbSchemaVersion = root.getInt("dbSchemaVersion"),
            createdAt = root.getLong("createdAt"),
            preferenceFiles = preferenceFiles,
        )
    }

    fun requireCurrent(
        manifest: BackupManifest,
        currentFormatVersion: Int,
        currentSchemaVersion: Int,
        currentPreferenceFiles: List<String>,
    ) {
        if (manifest.backupFormatVersion != currentFormatVersion) {
            throw IllegalStateException(
                "备份格式版本 ${manifest.backupFormatVersion} 与当前版本 $currentFormatVersion 不一致，" +
                    "只能恢复当前格式的备份。",
            )
        }
        if (manifest.dbSchemaVersion != currentSchemaVersion) {
            throw IllegalStateException(
                "备份数据库版本 ${manifest.dbSchemaVersion} 与当前版本 $currentSchemaVersion 不一致，" +
                    "只能恢复由当前版本导出的备份。",
            )
        }
        val expectedFiles = currentPreferenceFiles.sorted()
        if (manifest.preferenceFiles != expectedFiles) {
            throw IllegalStateException("备份中的配置文件清单与当前版本不一致")
        }
    }
}
