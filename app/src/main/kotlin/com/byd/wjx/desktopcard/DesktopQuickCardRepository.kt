package com.byd.wjx.desktopcard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.byd.wjx.storage.SoundLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class DesktopQuickCardRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val soundLibrary: SoundLibrary,
) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val iconDirectory = File(appContext.filesDir, ICON_DIRECTORY).apply(File::mkdirs)
    private val _icons = MutableStateFlow(loadIconChoices())
    val icons: StateFlow<List<DesktopQuickCardIcon>> = _icons.asStateFlow()
    private val _config = MutableStateFlow(load(validateSounds = true))
    val config: StateFlow<DesktopQuickCardConfig> = _config.asStateFlow()
    private val _runtimeRevision = MutableStateFlow(0L)
    val runtimeRevision: StateFlow<Long> = _runtimeRevision.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_CUSTOM_ICONS) _icons.value = loadIconChoices()
        _config.value = load(validateSounds = false)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setEnabled(value: Boolean) = update { copy(enabled = value) }
    fun setFloatingIconId(value: String) = update { copy(floatingIconId = value) }
    fun setFloatingIconSizeDp(value: Float) = update { copy(floatingIconSizeDp = value) }
    fun setFunctionCommands(value: List<String>) = update { copy(functionCommands = value) }
    fun setSoundPaths(value: List<String>) = update { copy(soundPaths = value) }
    fun notifyOverlayPermissionChanged() {
        _runtimeRevision.value += 1L
    }

    @Synchronized
    fun importCustomIcon(uri: Uri): Result<DesktopQuickCardIcon> {
        var destination: File? = null
        return runCatching {
            val displayName = queryDisplayName(uri).ifBlank { "自定义图标" }
            val extension = resolveExtension(uri, displayName)
            val id = "custom_${UUID.randomUUID()}"
            val target = File(iconDirectory, "$id.$extension").also { destination = it }
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选图片" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "所选文件不是可识别的图片" }
            val icon = DesktopQuickCardIcon(
                id = id,
                label = displayName.substringBeforeLast('.').ifBlank { "自定义图标" },
                filePath = target.absolutePath,
                custom = true,
            )
            val customs = _icons.value.filter(DesktopQuickCardIcon::custom) + icon
            persistCustomIcons(customs)
            _icons.value = DesktopQuickCardIconCatalog.builtIns + customs
            setFloatingIconId(icon.id)
            icon
        }.onFailure {
            destination?.delete()
        }
    }

    @Synchronized
    fun deleteCustomIcon(id: String) {
        val icon = _icons.value.firstOrNull { it.id == id && it.custom } ?: return
        icon.filePath?.let(::File)?.takeIf { it.parentFile == iconDirectory }?.delete()
        val customs = _icons.value.filter { it.custom && it.id != id }
        persistCustomIcons(customs)
        _icons.value = DesktopQuickCardIconCatalog.builtIns + customs
        if (_config.value.floatingIconId == id) setFloatingIconId(DESKTOP_QUICK_CARD_ICON_TOOLBOX)
    }

    fun iconForId(id: String): DesktopQuickCardIcon =
        _icons.value.firstOrNull { it.id == id } ?: DesktopQuickCardIconCatalog.default

    fun savePosition(x: Int, y: Int) = update {
        copy(x = x, y = y, positionInitialized = true)
    }

    fun refresh() {
        val sanitized = sanitize(_config.value, validateSounds = true)
        if (sanitized != _config.value) write(sanitized) else _config.value = sanitized
    }

    private fun update(transform: DesktopQuickCardConfig.() -> DesktopQuickCardConfig) {
        write(sanitize(_config.value.transform(), validateSounds = false))
    }

    private fun sanitize(
        raw: DesktopQuickCardConfig,
        validateSounds: Boolean,
    ): DesktopQuickCardConfig =
        sanitizeDesktopQuickCardConfig(
            raw = raw,
            validSoundPaths = if (validateSounds) {
                soundLibrary.listSounds().mapTo(linkedSetOf()) { it.file.absolutePath }
            } else {
                null
            },
            validIconIds = _icons.value.mapTo(linkedSetOf(), DesktopQuickCardIcon::id),
        )

    private fun load(validateSounds: Boolean): DesktopQuickCardConfig {
        val raw = DesktopQuickCardConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            functionCommands = if (prefs.contains(KEY_FUNCTION_COMMANDS)) {
                readList(KEY_FUNCTION_COMMANDS)
            } else {
                emptyList()
            },
            soundPaths = readList(KEY_SOUND_PATHS),
            floatingIconId = prefs.getString(KEY_FLOATING_ICON_ID, DESKTOP_QUICK_CARD_ICON_TOOLBOX)
                ?: DESKTOP_QUICK_CARD_ICON_TOOLBOX,
            floatingIconSizeDp = prefs.getFloat(
                KEY_FLOATING_ICON_SIZE_DP,
                DESKTOP_QUICK_CARD_DEFAULT_ICON_DP,
            ),
            x = prefs.getInt(KEY_X, 0),
            y = prefs.getInt(KEY_Y, 0),
            positionInitialized = prefs.getBoolean(KEY_POSITION_INITIALIZED, false),
        )
        return sanitize(raw, validateSounds)
    }

    private fun write(value: DesktopQuickCardConfig) {
        _config.value = value
        persist(value)
    }

    private fun persist(value: DesktopQuickCardConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putString(KEY_FUNCTION_COMMANDS, JSONArray(value.functionCommands).toString())
            .putString(KEY_SOUND_PATHS, JSONArray(value.soundPaths).toString())
            .putString(KEY_FLOATING_ICON_ID, value.floatingIconId)
            .putFloat(KEY_FLOATING_ICON_SIZE_DP, value.floatingIconSizeDp)
            .putInt(KEY_X, value.x)
            .putInt(KEY_Y, value.y)
            .putBoolean(KEY_POSITION_INITIALIZED, value.positionInitialized)
            .apply()
    }

    private fun loadIconChoices(): List<DesktopQuickCardIcon> =
        DesktopQuickCardIconCatalog.builtIns + readCustomIcons()

    private fun readCustomIcons(): List<DesktopQuickCardIcon> = runCatching {
        val array = JSONArray(prefs.getString(KEY_CUSTOM_ICONS, "[]") ?: "[]")
        buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val id = item.optString("id").trim()
                val fileName = File(item.optString("fileName")).name
                val file = File(iconDirectory, fileName)
                if (id.startsWith("custom_") && file.isFile) {
                    add(
                        DesktopQuickCardIcon(
                            id = id,
                            label = item.optString("label", "自定义图标").trim().ifBlank { "自定义图标" },
                            filePath = file.absolutePath,
                            custom = true,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun persistCustomIcons(icons: List<DesktopQuickCardIcon>) {
        val array = JSONArray()
        icons.forEach { icon ->
            val path = icon.filePath ?: return@forEach
            array.put(
                JSONObject()
                    .put("id", icon.id)
                    .put("label", icon.label)
                    .put("fileName", File(path).name),
            )
        }
        prefs.edit().putString(KEY_CUSTOM_ICONS, array.toString()).apply()
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }
            .orEmpty()
    }.getOrDefault("")

    private fun resolveExtension(uri: Uri, displayName: String): String {
        val mimeExtension = appContext.contentResolver.getType(uri)
            ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
        val nameExtension = displayName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        return mimeExtension?.takeIf(String::isNotBlank) ?: nameExtension ?: "img"
    }

    private fun readList(key: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList(array.length()) {
            repeat(array.length()) { index -> add(array.optString(index)) }
        }
    }.getOrDefault(emptyList())

    companion object {
        const val PREFS_NAME = "wjx_desktop_cards"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FUNCTION_COMMANDS = "function_commands"
        private const val KEY_SOUND_PATHS = "sound_paths"
        private const val KEY_FLOATING_ICON_ID = "floating_icon_id"
        private const val KEY_FLOATING_ICON_SIZE_DP = "floating_icon_size_dp"
        private const val KEY_CUSTOM_ICONS = "custom_icons"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_POSITION_INITIALIZED = "position_initialized"
        private const val ICON_DIRECTORY = "desktop_quick_card_icons"
    }
}
