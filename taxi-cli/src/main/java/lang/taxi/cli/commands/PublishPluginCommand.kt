package lang.taxi.cli.commands

import lang.taxi.cli.config.TaxiConfig
import lang.taxi.cli.config.TaxiEnvironment
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.internal.PublishPluginPlugin
import org.springframework.stereotype.Component

/**
 * Plugin which will publish a Shipman Plugin to shipman,
 * to make it available for other projects
 */
@Component
class PublishPluginCommand(val config: TaxiConfig, val pluginManager: PluginRegistry) : ShellCommand {
    override val name: String = "publish"

    override fun execute(env: TaxiEnvironment) {
        val plugin = pluginManager.declaredPlugins
                .filterIsInstance<PublishPluginPlugin>()
                .first()

        plugin.publish()
    }

}
