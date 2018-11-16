package lang.taxi.cli.plugins

import lang.taxi.plugins.Artifact
import java.io.File

class CompositeExternalPluginProvider(val externalProviders: List<ExternalPluginProvider>) : ExternalPluginProvider {
    override fun resolvePlugins(pluginIdentifiers: List<Artifact>): Map<Artifact, File> {
        return externalProviders.flatMap { it.resolvePlugins(pluginIdentifiers).toList() }.toMap()
    }

}
