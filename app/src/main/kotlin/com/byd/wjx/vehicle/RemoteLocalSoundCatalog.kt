package com.byd.wjx.vehicle

import java.io.File
import java.security.MessageDigest

internal data class RemoteLocalSoundReference(
    val soundKey: String,
    val name: String,
)

internal object RemoteLocalSoundCatalog {
    private val soundKeyRegex = Regex("^[a-f0-9]{64}$")

    fun parseReference(soundKey: String, name: String): RemoteLocalSoundReference? {
        val normalizedKey = soundKey.trim().lowercase()
        val normalizedName = name.trim()
        if (!normalizedKey.matches(soundKeyRegex) || normalizedName.isBlank()) return null
        return RemoteLocalSoundReference(normalizedKey, normalizedName)
    }

    fun listFiles(
        rootDirectory: File,
        supportedExtensions: Set<String>,
        maxItems: Int,
    ): List<File> {
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return emptyList()
        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { file ->
                file.isFile &&
                    file.extension.lowercase() in supportedExtensions &&
                    runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false)
            }
            .sortedBy { it.name.lowercase() }
            .take(maxItems.coerceAtLeast(0))
            .toList()
    }

    fun soundKey(file: File): String {
        val identity = "${file.name}\n${file.length()}\n${file.lastModified()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }
}
