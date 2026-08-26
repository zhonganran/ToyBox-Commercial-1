package com.byd.wjx.ui.settings

import com.byd.wjx.log.WjxFileLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface ServiceQrImageUpdate {
    data class Available(val bytes: ByteArray) : ServiceQrImageUpdate
    data object Missing : ServiceQrImageUpdate
    data object Failed : ServiceQrImageUpdate
}

@Singleton
class SettingsServiceQrCoordinator @Inject constructor(
    private val repository: ServiceQrRepository,
) {
    fun refreshUpdates(fallbackCodes: Map<String, ServiceQrCode>): Flow<SettingsServiceQrUiState> = flow {
        val cached = runCatching(repository::cached).getOrDefault(emptyMap())
            .ifEmpty { fallbackCodes }
        emit(SettingsServiceQrUiState(codes = cached, loading = true))
        try {
            val refreshed = repository.refresh()
            emit(
                SettingsServiceQrUiState(
                    codes = refreshed,
                    message = if (refreshed.isEmpty()) "后台暂未配置二维码" else "二维码已更新",
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "service qr refresh failed: ${error.message}")
            emit(
                SettingsServiceQrUiState(
                    codes = cached,
                    message = if (cached.isNotEmpty()) {
                        "网络不可用，已显示缓存二维码"
                    } else {
                        "二维码读取失败：${error.message ?: "网络异常"}"
                    },
                    messageIsError = cached.isEmpty(),
                ),
            )
        }
    }

    fun imageUpdates(key: String, imageUrl: String): Flow<ServiceQrImageUpdate> = flow {
        val cached = repository.cachedImage(key, imageUrl)
        if (cached != null) emit(ServiceQrImageUpdate.Available(cached))
        if (imageUrl.isBlank()) {
            if (cached == null) emit(ServiceQrImageUpdate.Missing)
            return@flow
        }
        try {
            val refreshed = repository.refreshImage(key, imageUrl)
            if (cached == null || !cached.contentEquals(refreshed)) {
                emit(ServiceQrImageUpdate.Available(refreshed))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            WjxFileLogger.w(TAG, "service qr image refresh failed key=$key: ${error.message}")
            if (cached == null) emit(ServiceQrImageUpdate.Failed)
        }
    }

    companion object {
        private const val TAG = "SettingsServiceQr"
    }
}
