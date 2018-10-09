package lang.taxi.cli.plugins

import lang.taxi.plugins.Artifact

interface PluginArtifact {
    fun getPlugin(): Plugin
    fun getArtifact(): Artifact
}

class LocalPluginAdapter(val plugin: InternalPlugin) : PluginArtifact {
    override fun getPlugin(): Plugin = plugin
    override fun getArtifact(): Artifact = plugin.artifact
}
