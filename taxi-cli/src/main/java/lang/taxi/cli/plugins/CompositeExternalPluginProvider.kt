package lang.taxi.cli.plugins

import lang.taxi.plugins.Artifact
import java.io.File

class CompositeExternalPluginProvider(val providerExternals: List<ExternalPluginProvider>) : ExternalPluginProvider {
    override fun resolvePlugins(pluginIdentifiers: List<Artifact>): Map<Artifact, File> {
        return providerExternals.flatMap { it.resolvePlugins(pluginIdentifiers).toList() }.toMap()
    }

}
