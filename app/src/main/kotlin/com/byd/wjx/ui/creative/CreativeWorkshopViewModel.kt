package com.byd.wjx.ui.creative

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byd.wjx.BuildConfig
import com.byd.wjx.vehicle.VehicleIdentityReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

data class CreativeWorkshopUiState(
    val loadingCatalog: Boolean = true,
    val catalog: CreativeWorkshopCatalog? = null,
    val selectedVehicle: CreativeVehicle? = null,
    val paintColor: String = CreativePaintOptions.first().color,
    val selectedWrapId: String? = null,
    val windowOpacity: Float = 0.72f,
    val showVehicleSelector: Boolean = false,
    val vehicleSelectionRequired: Boolean = false,
    val viewerLoading: Boolean = false,
    val viewerError: String? = null,
    val catalogError: String? = null,
)

@HiltViewModel
class CreativeWorkshopViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
    private val vehicleIdentityReader: VehicleIdentityReader,
) : ViewModel() {
    private val preferences = CreativeWorkshopPreferences(context)
    private val _uiState = MutableStateFlow(
        CreativeWorkshopUiState(
            paintColor = preferences.paintColor,
            selectedWrapId = preferences.selectedWrapId,
            windowOpacity = preferences.windowOpacity,
        ),
    )
    val uiState: StateFlow<CreativeWorkshopUiState> = _uiState.asStateFlow()

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        _uiState.update { it.copy(loadingCatalog = true, catalogError = null) }
        viewModelScope.launch {
            runCatching { loadCatalog() }
                .onSuccess(::applyCatalog)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingCatalog = false,
                            catalogError = error.message ?: "在线车型目录加载失败",
                        )
                    }
                }
        }
    }

    fun openVehicleSelector() {
        _uiState.update { it.copy(showVehicleSelector = true) }
    }

    fun closeVehicleSelector() {
        _uiState.update { state ->
            if (state.vehicleSelectionRequired) state else state.copy(showVehicleSelector = false)
        }
    }

    fun selectVehicle(vehicle: CreativeVehicle) {
        preferences.selectedVehicleId = vehicle.id
        _uiState.update {
            it.copy(
                selectedVehicle = vehicle,
                showVehicleSelector = false,
                vehicleSelectionRequired = false,
                viewerLoading = true,
                viewerError = null,
            )
        }
    }

    fun selectPaint(option: CreativePaintOption) {
        preferences.paintColor = option.color
        preferences.selectedWrapId = null
        _uiState.update { it.copy(paintColor = option.color, selectedWrapId = null) }
    }

    fun selectWrap(wrap: CreativeWrap?) {
        preferences.selectedWrapId = wrap?.id
        _uiState.update { it.copy(selectedWrapId = wrap?.id) }
    }

    fun setWindowOpacity(value: Float) {
        val normalized = value.coerceIn(0.12f, 0.92f)
        preferences.windowOpacity = normalized
        _uiState.update { it.copy(windowOpacity = normalized) }
    }

    fun resetDesign() {
        val defaultPaint = CreativePaintOptions.first().color
        preferences.paintColor = defaultPaint
        preferences.selectedWrapId = null
        preferences.windowOpacity = DEFAULT_WINDOW_OPACITY
        _uiState.update {
            it.copy(
                paintColor = defaultPaint,
                selectedWrapId = null,
                windowOpacity = DEFAULT_WINDOW_OPACITY,
            )
        }
    }

    fun onViewerState(payload: String) {
        val message = runCatching { JSONObject(payload) }.getOrNull() ?: return
        when (message.optString("type")) {
            "loading" -> _uiState.update { it.copy(viewerLoading = true, viewerError = null) }
            "ready" -> _uiState.update { it.copy(viewerLoading = false, viewerError = null) }
            "error" -> _uiState.update {
                it.copy(
                    viewerLoading = false,
                    viewerError = message.optString("message").ifBlank { "3D 车型加载失败" },
                )
            }
        }
    }

    private suspend fun loadCatalog(): CreativeWorkshopCatalog = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.CREATIVE_WORKSHOP_CATALOG_URL)
            .header("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("在线车型目录请求失败（${response.code}）")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("在线车型目录为空")
            CreativeWorkshopCatalog.parse(body)
        }
    }

    private fun applyCatalog(catalog: CreativeWorkshopCatalog) {
        val manualSelection = preferences.selectedVehicleId
            ?.let { selectedId -> catalog.vehicles.firstOrNull { it.id == selectedId } }
        val automaticSelection = if (manualSelection == null && preferences.selectedVehicleId == null) {
            CreativeVehicleMatcher.findBestMatch(
                vehicleInfo = vehicleIdentityReader.snapshot(),
                vehicles = catalog.vehicles,
            )
        } else {
            null
        }
        val selectedVehicle = manualSelection ?: automaticSelection
        val selectedWrapId = preferences.selectedWrapId
            ?.takeIf { id -> catalog.wraps.any { it.id == id } }
        if (selectedWrapId == null && preferences.selectedWrapId != null) {
            preferences.selectedWrapId = null
        }
        _uiState.update {
            it.copy(
                loadingCatalog = false,
                catalog = catalog,
                selectedVehicle = selectedVehicle,
                selectedWrapId = selectedWrapId,
                showVehicleSelector = selectedVehicle == null,
                vehicleSelectionRequired = selectedVehicle == null,
                viewerLoading = selectedVehicle != null,
                catalogError = null,
            )
        }
    }

    private class CreativeWorkshopPreferences(context: Context) {
        private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        var selectedVehicleId: String?
            get() = preferences.getString(KEY_VEHICLE_ID, null)?.takeIf(String::isNotBlank)
            set(value) {
                preferences.edit().apply {
                    if (value.isNullOrBlank()) remove(KEY_VEHICLE_ID) else putString(KEY_VEHICLE_ID, value)
                }.apply()
            }

        var paintColor: String
            get() = preferences.getString(KEY_PAINT_COLOR, CreativePaintOptions.first().color)
                ?.takeIf { it.matches(HEX_COLOR) }
                ?: CreativePaintOptions.first().color
            set(value) {
                preferences.edit().putString(KEY_PAINT_COLOR, value).apply()
            }

        var selectedWrapId: String?
            get() = preferences.getString(KEY_WRAP_ID, null)?.takeIf(String::isNotBlank)
            set(value) {
                preferences.edit().apply {
                    if (value.isNullOrBlank()) remove(KEY_WRAP_ID) else putString(KEY_WRAP_ID, value)
                }.apply()
            }

        var windowOpacity: Float
            get() = preferences.getFloat(KEY_WINDOW_OPACITY, DEFAULT_WINDOW_OPACITY)
                .coerceIn(0.12f, 0.92f)
            set(value) {
                preferences.edit().putFloat(KEY_WINDOW_OPACITY, value.coerceIn(0.12f, 0.92f)).apply()
            }
    }

    companion object {
        private const val PREFERENCES_NAME = "creative_workshop"
        private const val KEY_VEHICLE_ID = "vehicle_id"
        private const val KEY_PAINT_COLOR = "paint_color"
        private const val KEY_WRAP_ID = "wrap_id"
        private const val KEY_WINDOW_OPACITY = "window_opacity"
        private const val DEFAULT_WINDOW_OPACITY = 0.72f
        private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
