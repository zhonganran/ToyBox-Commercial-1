package com.byd.wjx.data.dashcam

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.byd.wjx.log.WjxFileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DashcamLibraryState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val clips: List<DashcamVideoClip> = emptyList(),
    val scanning: Boolean = false,
    val error: String? = null,
)

@Singleton
class DashcamFolderRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scanMutex = Mutex()
    private val durationCache = DashcamDurationCache(
        File(appContext.noBackupFilesDir, DURATION_CACHE_FILE_NAME),
    )
    private val _state = MutableStateFlow(loadSelection())
    val state: StateFlow<DashcamLibraryState> = _state.asStateFlow()

    suspend fun selectFolder(uri: Uri) = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            val previousUri = _state.value.folderUri?.let(Uri::parse)
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val folder = requireNotNull(DocumentFile.fromTreeUri(appContext, uri))
                require(folder.isDirectory && folder.canRead())
                val selection = DashcamLibraryState(
                    folderUri = uri.toString(),
                    folderName = folder.name?.takeIf(String::isNotBlank) ?: "已选择文件夹",
                )
                prefs.edit()
                    .putString(KEY_FOLDER_URI, selection.folderUri)
                    .putString(KEY_FOLDER_NAME, selection.folderName)
                    .apply()
                if (previousUri != uri) {
                    clearDurationCache()
                    previousUri?.let(::releaseFolderPermission)
                }
                scanSelection(selection)
            }.onFailure {
                if (previousUri != uri) releaseFolderPermission(uri)
                WjxFileLogger.w(TAG, "select folder failed: ${it.javaClass.simpleName}")
                _state.value = _state.value.copy(error = "无法读取所选文件夹，请重新选择")
            }
        }
    }

    suspend fun refresh() = scanMutex.withLock {
        val selection = _state.value
        val uri = selection.folderUri?.let(Uri::parse) ?: return
        withContext(Dispatchers.IO) { scanSelection(selection, uri) }
    }

    suspend fun clearFolder() = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            _state.value.folderUri?.let(Uri::parse)?.let(::releaseFolderPermission)
            prefs.edit().remove(KEY_FOLDER_URI).remove(KEY_FOLDER_NAME).apply()
            clearDurationCache()
            _state.value = DashcamLibraryState()
        }
    }

    private fun scanSelection(
        selection: DashcamLibraryState,
        uri: Uri = requireNotNull(selection.folderUri).let(Uri::parse),
    ) {
        _state.value = selection.copy(scanning = true, error = null)
        val result = scanFolder(uri)
        _state.value = result.fold(
            onSuccess = { clips ->
                WjxFileLogger.i(TAG, "dashcam folder scanned clips=${clips.size}")
                selection.copy(clips = clips, scanning = false, error = null)
            },
            onFailure = {
                WjxFileLogger.w(TAG, "dashcam folder scan failed: ${it.javaClass.simpleName}")
                selection.copy(
                    clips = emptyList(),
                    scanning = false,
                    error = "录像文件夹读取失败，请检查存储卡或重新选择",
                )
            },
        )
    }

    private fun scanFolder(uri: Uri): Result<List<DashcamVideoClip>> = runCatching {
        val folder = requireNotNull(DocumentFile.fromTreeUri(appContext, uri))
        require(folder.isDirectory && folder.canRead())
        val activeFingerprints = mutableSetOf<DashcamVideoFingerprint>()
        val files = folder.listFiles().asSequence()
            .filter(DocumentFile::isFile)
            .mapNotNull { file ->
                val name = file.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                if (!file.isSupportedVideo(name)) return@mapNotNull null
                val startTs = DashcamVideoTimeline.parseStartTimestamp(name) ?: return@mapNotNull null
                DashcamVideoFile(
                    uri = file.uri.toString(),
                    displayName = name,
                    startTs = startTs,
                    durationMs = readVideoDuration(file, activeFingerprints),
                )
            }
            .toList()
        durationCache.retainAll(activeFingerprints)
        if (!durationCache.persist()) {
            WjxFileLogger.w(TAG, "dashcam duration cache write failed")
        }
        DashcamVideoTimeline.buildClips(files)
    }

    private fun readVideoDuration(
        file: DocumentFile,
        activeFingerprints: MutableSet<DashcamVideoFingerprint>,
    ): Long? {
        val fingerprint = DashcamVideoFingerprint(
            uri = file.uri.toString(),
            length = file.length(),
            lastModified = file.lastModified(),
        )
        activeFingerprints += fingerprint
        durationCache[fingerprint]?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, file.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it in MIN_VIDEO_DURATION_MS..MAX_VIDEO_DURATION_MS }
                ?.also { durationCache.put(fingerprint, it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun releaseFolderPermission(uri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure {
            WjxFileLogger.w(TAG, "release folder permission failed: ${it.javaClass.simpleName}")
        }
    }

    private fun clearDurationCache() {
        if (!durationCache.clear()) {
            WjxFileLogger.w(TAG, "dashcam duration cache clear failed")
        }
    }

    private fun loadSelection(): DashcamLibraryState = DashcamLibraryState(
        folderUri = prefs.getString(KEY_FOLDER_URI, null),
        folderName = prefs.getString(KEY_FOLDER_NAME, null),
    )

    private fun DocumentFile.isSupportedVideo(displayName: String): Boolean {
        if (type?.startsWith("video/", ignoreCase = true) == true) return true
        return displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase() in VIDEO_EXTENSIONS
    }

    private companion object {
        const val TAG = "DashcamFolder"
        const val PREFS_NAME = "wjx_dashcam_folder"
        const val KEY_FOLDER_URI = "folder_uri"
        const val KEY_FOLDER_NAME = "folder_name"
        const val DURATION_CACHE_FILE_NAME = "dashcam_duration_cache.properties"
        const val MIN_VIDEO_DURATION_MS = 1_000L
        const val MAX_VIDEO_DURATION_MS = 24L * 60L * 60L * 1_000L
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "3gp", "ts", "mkv", "avi")
    }
}
