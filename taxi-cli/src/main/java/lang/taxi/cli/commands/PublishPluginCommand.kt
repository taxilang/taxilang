package lang.taxi.cli.commands

import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.internal.PublishPluginPlugin
import lang.taxi.generators.TaxiEnvironment
import org.springframework.stereotype.Component

/**
 * Plugin which will publish a Shipman Plugin to shipman,
 * to make it available for other projects
 */
@Component
class PublishPluginCommand(val pluginManager: PluginRegistry) : ShellCommand {
    override val name: String = "publish"

    override fun execute(environment: TaxiEnvironment) {
        val plugin = pluginManager.declaredPlugins
                .filterIsInstance<PublishPluginPlugin>()
                .first()

        plugin.publish()
    }

}
