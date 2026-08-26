package com.byd.wjx.data.system

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the installed APK signing identity used by local device-bound encryption. */
@Singleton
class AppSigningCertificateDigestProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun currentSha256(): ByteArray? {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val certificates = packageInfo.signingInfo?.apkContentsSigners
            ?.map { signature -> signature.toByteArray() }
            ?.takeIf(List<ByteArray>::isNotEmpty)
            ?: return null
        return digest(certificates)
    }

    companion object {
        internal fun digest(certificates: List<ByteArray>): ByteArray {
            require(certificates.isNotEmpty()) { "At least one signing certificate is required" }
            val digests = certificates
                .map { certificate -> MessageDigest.getInstance("SHA-256").digest(certificate) }
                .sortedWith { first, second -> compareUnsigned(first, second) }
            if (digests.size == 1) return digests.single()
            return MessageDigest.getInstance("SHA-256").run {
                digests.forEach { digest ->
                    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(digest.size).array())
                    update(digest)
                }
                digest()
            }
        }

        private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
            for (index in 0 until minOf(first.size, second.size)) {
                val comparison = (first[index].toInt() and 0xff)
                    .compareTo(second[index].toInt() and 0xff)
                if (comparison != 0) return comparison
            }
            return first.size.compareTo(second.size)
        }
    }
}
