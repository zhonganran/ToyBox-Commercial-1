package com.byd.wjx.data.sound

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.byd.wjx.audio.LockSoundConfigSync
import com.byd.wjx.audio.LockSoundSettings
import com.byd.wjx.storage.SoundLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalSoundDeleteResult(
    val deleted: Boolean,
    val sounds: List<SoundLibrary.SoundItem>,
    val eventSoundSyncSucceeded: Boolean? = null,
)

internal data class LocalSoundImportTarget(
    val baseName: String,
    val extension: String,
)

/** Owns local sound file mutations shared by the library and vehicle-event settings screens. */
@Singleton
class LocalSoundLibraryCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val soundLibrary: SoundLibrary,
    private val lockSoundConfigSync: LockSoundConfigSync,
) {
    private val appContext = context.applicationContext
    private val lockSoundSettings = LockSoundSettings(appContext)

    suspend fun listSounds(): List<SoundLibrary.SoundItem> = withContext(Dispatchers.IO) {
        soundLibrary.listSounds()
    }

    suspend fun importSound(uri: Uri): Result<SoundLibrary.SoundItem> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            val target = resolveLocalSoundImportTarget(
                displayName = displayName,
                mimeType = appContext.contentResolver.getType(uri),
            )
            val input = appContext.contentResolver.openInputStream(uri)
                ?: error("无法读取所选音效")
            val saved = input.use {
                soundLibrary.saveSound(target.baseName, target.extension, it)
            }
            SoundLibrary.SoundItem(saved)
        }
    }

    suspend fun deleteSound(path: String): LocalSoundDeleteResult = withContext(Dispatchers.IO) {
        if (!soundLibrary.deleteSound(path)) {
            return@withContext LocalSoundDeleteResult(
                deleted = false,
                sounds = soundLibrary.listSounds(),
            )
        }
        lockSoundSettings.clearIfPath(path)
        val synced = runCatching { lockSoundConfigSync.sync(SYNC_REASON_DELETE) }
            .getOrDefault(false)
        LocalSoundDeleteResult(
            deleted = true,
            sounds = soundLibrary.listSounds(),
            eventSoundSyncSucceeded = synced,
        )
    }

    suspend fun moveSound(fromPath: String, toPath: String): List<SoundLibrary.SoundItem> =
        withContext(Dispatchers.IO) {
            soundLibrary.moveSound(fromPath, toPath)
            soundLibrary.listSounds()
        }

    private companion object {
        const val SYNC_REASON_DELETE = "local_sound_delete"
    }
}

internal fun resolveLocalSoundImportTarget(
    displayName: String?,
    mimeType: String?,
): LocalSoundImportTarget {
    val safeDisplayName = displayName.orEmpty().trim().ifBlank { "音效" }
    val namedExtension = safeDisplayName.substringAfterLast('.', "").lowercase()
    val extension = when {
        namedExtension in SoundLibrary.SUPPORTED_EXT -> namedExtension
        namedExtension.isNotBlank() -> null
        else -> soundExtensionForMimeType(mimeType)
    } ?: error("仅支持 MP3、WAV、AAC、M4A、OGG、FLAC")
    val baseName = if (namedExtension.isBlank()) {
        safeDisplayName
    } else {
        safeDisplayName.substringBeforeLast('.', safeDisplayName)
    }
    return LocalSoundImportTarget(baseName = baseName, extension = extension)
}

private fun soundExtensionForMimeType(mimeType: String?): String? = when (mimeType?.lowercase()) {
    "audio/mpeg", "audio/mp3" -> "mp3"
    "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
    "audio/aac" -> "aac"
    "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
    "audio/ogg" -> "ogg"
    "audio/flac", "audio/x-flac" -> "flac"
    else -> null
}
