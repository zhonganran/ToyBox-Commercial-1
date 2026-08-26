package com.byd.wjx.data.system

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Sole owner of app-scoped platform identity reads used for local device-bound encryption. */
@Singleton
class AppScopedDeviceBindingProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun derive(purpose: String): ByteArray? {
        require(purpose.matches(PURPOSE)) { "Invalid device-binding purpose" }
        val androidId = readAndroidId()?.takeIf(String::isNotBlank) ?: return null
        return derive(context.packageName, purpose, androidId)
    }

    @SuppressLint("HardwareIds")
    private fun readAndroidId(): String? = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID,
    )

    companion object {
        private val PURPOSE = Regex("""^[a-z0-9.-]{1,64}$""")

        internal fun derive(packageName: String, purpose: String, androidId: String): ByteArray =
            MessageDigest.getInstance("SHA-256").run {
                update(DOMAIN)
                update(0)
                update(packageName.toByteArray(StandardCharsets.UTF_8))
                update(0)
                update(purpose.toByteArray(StandardCharsets.UTF_8))
                update(0)
                update(androidId.toByteArray(StandardCharsets.UTF_8))
                digest()
            }

        private val DOMAIN = "com.byd.wjx.device-binding.v1"
            .toByteArray(StandardCharsets.US_ASCII)
    }
}
