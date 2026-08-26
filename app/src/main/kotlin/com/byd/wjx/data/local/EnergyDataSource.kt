package com.byd.wjx.data.local

import android.content.Context
import java.io.File

/** Owns discovery, availability, copying, and processed-file fingerprints. */
internal class EnergyDataSource(
    private val context: Context,
    private val directory: File,
) {
    fun directory(): File = directory

    fun findDatabase(directory: File = directory()): File? =
        findDatabaseFromListing(directory) ?: findKnownDatabase(directory)

    fun listFiles(directory: File): List<File>? = directory.listFiles()?.toList()

    fun knownDatabaseFiles(directory: File): List<File> = KNOWN_DATABASE_NAMES.map { File(directory, it) }

    fun isAvailable(): Boolean = directory.exists() &&
        directory.isDirectory &&
        findDatabase(directory)?.let { database ->
            database.canRead() && database.length() >= MIN_DATABASE_BYTES
        } == true

    fun peekSourceChanged(preferencesContext: Context): Boolean {
        val source = currentSource() ?: return false
        val prefs = preferencesContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return source.lastModified() != prefs.getLong(KEY_LAST_MODIFIED, 0L) ||
            source.length() != prefs.getLong(KEY_FILE_SIZE, 0L) ||
            source.absolutePath != prefs.getString(KEY_FILE_PATH, "")
    }

    fun markSourceProcessed(preferencesContext: Context) {
        val source = currentSource() ?: return
        preferencesContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_MODIFIED, source.lastModified())
            .putLong(KEY_FILE_SIZE, source.length())
            .putString(KEY_FILE_PATH, source.absolutePath)
            .apply()
    }

    @Synchronized
    fun copyToLocal(source: File): File = EnergyDatabaseSnapshotCopier(context).copy(source)

    private fun currentSource(): File? {
        if (!directory.exists()) return null
        return findDatabase(directory)
    }

    internal companion object {
        const val DEFAULT_DIRECTORY_PATH = "/storage/emulated/0/energydata"
        private const val PREFERENCES_NAME = "energydata_sync"
        private const val KEY_LAST_MODIFIED = "lastModified"
        private const val KEY_FILE_SIZE = "fileSize"
        private const val KEY_FILE_PATH = "filePath"
        private const val MIN_DATABASE_BYTES = 16L

        val KNOWN_DATABASE_NAMES = listOf(
            "EC_database.db",
            "energydata.db",
            "energy.db",
            "EnergyConsumption.db",
        )

        fun findDatabaseFromListing(directory: File): File? = directory.listFiles()
            ?.filter { file ->
                file.isFile && (file.name.endsWith(".db", true) || file.name.endsWith(".sqlite", true))
            }
            ?.maxByOrNull(File::lastModified)

        fun findKnownDatabase(directory: File): File? = KNOWN_DATABASE_NAMES
            .asSequence()
            .map { name -> File(directory, name) }
            .firstOrNull { file -> file.exists() && file.length() > 0L }
    }

}

/** Keeps filesystem probes bounded while allowing the active source directory to change. */
internal class EnergyDataAvailabilityCache {
    @Volatile private var cachedPath: String? = null
    @Volatile private var cachedAvailability: Boolean? = null
    @Volatile private var checkedAtNanos = 0L

    fun get(directory: File, probe: () -> Boolean): Boolean {
        val now = System.nanoTime()
        val cached = cachedAvailability
        if (cached != null && directory.absolutePath == cachedPath && now - checkedAtNanos < CACHE_NANOS) {
            return cached
        }
        return synchronized(this) {
            val refreshedNow = System.nanoTime()
            val refreshedCached = cachedAvailability
            if (refreshedCached != null &&
                directory.absolutePath == cachedPath &&
                refreshedNow - checkedAtNanos < CACHE_NANOS
            ) {
                refreshedCached
            } else {
                probe().also { available ->
                    cachedPath = directory.absolutePath
                    cachedAvailability = available
                    checkedAtNanos = refreshedNow
                }
            }
        }
    }

    private companion object {
        const val CACHE_NANOS = 15_000_000_000L
    }
}
