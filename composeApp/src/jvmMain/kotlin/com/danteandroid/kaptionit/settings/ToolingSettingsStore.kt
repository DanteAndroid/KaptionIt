package com.danteandroid.kaptionit.settings

import kotlinx.serialization.json.Json
import java.io.File

object ToolingSettingsStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    internal fun appDataDir(): File {
        val home = System.getProperty("user.home") ?: return File(".")
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> File(home, "Library/Application Support/kaptionit")
            os.contains("windows") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "kaptionit")
                else File(home, "kaptionit")
            }
            else -> {
                val cfg = System.getenv("XDG_CONFIG_HOME")
                if (!cfg.isNullOrBlank()) File(cfg, "kaptionit")
                else File(home, ".config/kaptionit")
            }
        }
    }

    private fun settingsFile(): File = File(appDataDir(), "kaptionit_settings.json")

    fun loadOrDefault(): ToolingSettings {
        val f = settingsFile()
        if (!f.isFile) return ToolingSettings.fromEnvironment().normalized()
        return try {
            json.decodeFromString<ToolingSettings>(f.readText()).normalized()
        } catch (_: Exception) {
            ToolingSettings.fromEnvironment().normalized()
        }
    }

    fun save(settings: ToolingSettings) {
        runCatching {
            appDataDir().mkdirs()
            settingsFile().writeText(json.encodeToString(settings.normalized()))
        }
    }
}
