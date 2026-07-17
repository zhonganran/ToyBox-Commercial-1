package com.byd.wjx.data.sound

import android.content.Context
import com.byd.wjx.storage.WjxDirectories
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SoundDownloadStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadRecords(): List<SoundDownloadRecord> {
        val raw = prefs.getString(KEY_RECORDS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { index ->
                arr.optJSONObject(index)?.let { obj ->
                    SoundDownloadRecord(
                        soundId = obj.optLong("soundId"),
                        name = obj.optString("name"),
                        fileName = obj.optString("fileName"),
                        sizeLabel = obj.optString("sizeLabel"),
                        finishedAt = obj.optLong("finishedAt"),
                    )
                }
            }.filter {
                it.soundId > 0L &&
                    it.name.isNotBlank() &&
                    it.fileName.isNotBlank() &&
                    File(WjxDirectories.soundDir, it.fileName).isFile
            }
        }.getOrDefault(emptyList())
    }

    fun findExistingRecord(soundId: Long): SoundDownloadRecord? {
        return loadRecords().firstOrNull { record ->
            record.soundId == soundId &&
                File(WjxDirectories.soundDir, record.fileName).isFile
        }
    }

    fun add(record: SoundDownloadRecord) {
        val records = (listOf(record) + loadRecords().filterNot {
            it.soundId == record.soundId
        })
            .take(MAX_RECORDS)
        val ids = loadDownloadedIds() + record.soundId
        prefs.edit()
            .putString(KEY_RECORDS, records.toJson())
            .putStringSet(KEY_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    fun remove(record: SoundDownloadRecord) {
        val records = loadRecords().filterNot {
                it.soundId == record.soundId &&
                it.fileName == record.fileName &&
                it.finishedAt == record.finishedAt
        }
        save(records)
    }

    fun removeByFileName(fileName: String) {
        save(loadRecords().filterNot {
            it.fileName == fileName
        })
    }

    private fun save(records: List<SoundDownloadRecord>) {
        val ids = records.map { it.soundId }.toSet()
        prefs.edit()
            .putString(KEY_RECORDS, records.toJson())
            .putStringSet(KEY_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun loadDownloadedIds(): Set<Long> {
        return prefs.getStringSet(KEY_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    private fun List<SoundDownloadRecord>.toJson(): String {
        val arr = JSONArray()
        forEach { record ->
            arr.put(
                JSONObject()
                    .put("soundId", record.soundId)
                    .put("name", record.name)
                    .put("fileName", record.fileName)
                    .put("sizeLabel", record.sizeLabel)
                    .put("finishedAt", record.finishedAt)
            )
        }
        return arr.toString()
    }

    companion object {
        private const val PREF_NAME = "wjx_sound_downloads"
        private const val KEY_RECORDS = "records"
        private const val KEY_IDS = "ids"
        private const val MAX_RECORDS = 50
    }

}

data class SoundDownloadRecord(
    val soundId: Long,
    val name: String,
    val fileName: String,
    val sizeLabel: String,
    val finishedAt: Long,
)
