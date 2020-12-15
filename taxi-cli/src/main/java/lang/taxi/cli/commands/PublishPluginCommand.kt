package lang.taxi.cli.commands

import com.beust.jcommander.Parameters
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.internal.PublishPluginPlugin
import lang.taxi.generators.TaxiProjectEnvironment
import org.springframework.stereotype.Component

/**
 * Plugin which will publish a Shipman Plugin to shipman,
 * to make it available for other projects
 */
@Component
@Parameters(commandDescription = "Publishes a taxi plugin to a plugin registry. (Experimental)")
class PublishPluginCommand(val pluginManager: PluginRegistry) : ProjectShellCommand {
    override val name: String = "publish"

    override fun execute(environment: TaxiProjectEnvironment) {
        val plugin = pluginManager.declaredPlugins
                .filterIsInstance<PublishPluginPlugin>()
                .first()

        plugin.publish()
    }

}
