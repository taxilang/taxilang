package lang.taxi.cli.config

import lang.taxi.cli.TaxiProject
import java.io.File

/**
 * Models the settings loaded from multiple settings files.
 * These are merged together.
 */
data class TaxiConfig(
        val project: TaxiProject,
        val pluginSettings: PluginSettings = PluginSettings()
)

data class PluginSettings(val repositories: List<String> = emptyList(), val localCache: String = "~/.taxi/plugins") {
    val localCachePath by lazy {
        val path = this.localCache.replace("~", System.getProperty("user.home"))
        File(path)
    }
}