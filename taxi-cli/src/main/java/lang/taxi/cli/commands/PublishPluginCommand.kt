package lang.taxi.cli.commands

import com.beust.jcommander.Parameters
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.internal.PublishPluginPlugin
import lang.taxi.generators.TaxiProjectEnvironment
import org.springframework.stereotype.Component

/**
 * Plugin which will publish a taxi Plugin to a repository,
 * to make it available for other projects
 */
@Component
@Parameters(commandDescription = "Publishes a taxi plugin to a plugin repository. (Experimental)")
class PublishPluginCommand(val pluginManager: PluginRegistry) : ProjectShellCommand {
    override val name: String = "publish-plugin"

    override fun execute(environment: TaxiProjectEnvironment) {
        val plugin = pluginManager.declaredPlugins
                .filterIsInstance<PublishPluginPlugin>()
                .first()

        plugin.publish()
    }

}
