package com.byd.wjx.service

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal class UpdateApkVerifier {
    fun verify(
        context: Context,
        file: File,
        update: UpdateChecker.UpdateInfo,
    ) {
        require(file.isFile && file.length() > 0L) { "下载的安装包不存在" }
        require(update.apkSize <= 0L || file.length() == update.apkSize) { "安装包大小校验失败" }
        require(fileSha256(file) == normalizeSha256(update.sha256)) { "安装包 SHA-256 校验失败" }

        val packageManager = context.packageManager
        val archive = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: throw IllegalStateException("无法读取安装包信息")
        require(archive.packageName == context.packageName && archive.packageName == update.packageName) {
            "安装包包名校验失败"
        }
        require(archive.longVersionCode == update.versionCode) { "安装包版本码校验失败" }

        val archiveCertificates = certificateSha256(archive)
        val installed = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val installedCertificates = certificateSha256(installed)
        val expectedCertificate = normalizeSha256(update.certificateSha256)
        require(expectedCertificate in archiveCertificates) { "安装包证书与更新信息不一致" }
        require(archiveCertificates.any(installedCertificates::contains)) { "安装包签名与当前应用不一致" }
    }

    private fun certificateSha256(packageInfo: PackageInfo): Set<String> {
        val signingInfo = packageInfo.signingInfo
            ?: throw IllegalStateException("安装包缺少签名信息")
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return signatures
            .map { signature -> sha256(signature.toByteArray()) }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("安装包缺少签名证书")
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun normalizeSha256(value: String): String = value
        .trim()
        .lowercase()
        .filter { it in '0'..'9' || it in 'a'..'f' }
}
