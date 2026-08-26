package com.byd.wjx.data.autoservice

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/** Owns strict parsing and crash-resilient replacement of the app-private ADB key files. */
internal class AdbKeyFiles(filesDir: File) {
    private val keyDir = File(filesDir, "adb_keys")
    private val privateFile = File(keyDir, "adb_key.priv")
    private val publicFile = File(keyDir, "adb_key.pub")

    fun load(): KeyPair? {
        if (!privateFile.exists() || !publicFile.exists()) return null
        val factory = KeyFactory.getInstance("RSA")
        val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes()))
        val publicKey = factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes()))
        return KeyPair(publicKey, privateKey).also(AdbIdentityBackupCodec::validateKeyPair)
    }

    fun persist(pair: KeyPair) {
        AdbIdentityBackupCodec.validateKeyPair(pair)
        check(keyDir.exists() || keyDir.mkdirs()) { "Could not create ADB key directory" }
        val privateTemp = File.createTempFile("adb_key_", ".priv.tmp", keyDir)
        val publicTemp = File.createTempFile("adb_key_", ".pub.tmp", keyDir)
        try {
            writeAndSync(privateTemp, pair.private.encoded)
            writeAndSync(publicTemp, pair.public.encoded)
            moveReplacing(privateTemp, privateFile)
            moveReplacing(publicTemp, publicFile)
        } finally {
            privateTemp.delete()
            publicTemp.delete()
        }
    }

    private fun writeAndSync(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
