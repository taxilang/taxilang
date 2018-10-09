package lang.taxi.cli

import lang.taxi.cli.config.TaxiConfig
import lang.taxi.cli.plugins.ExternalPluginProvider
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.RemoteExternalPluginProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ro.fortsoft.pf4j.DefaultPluginManager
import java.net.URI

@Configuration
class PluginConfig {

    @Bean
    internal fun pluginRegistry(externalPluginProviders: List<ExternalPluginProvider>, internalPlugins: List<InternalPlugin>, config: TaxiConfig): PluginRegistry {
        val pluginDirectory = config.pluginSettings.localCachePath.toPath()
        val pluginManager = DefaultPluginManager(pluginDirectory)
        return PluginRegistry(externalPluginProviders, internalPlugins, config.project.pluginArtifacts, pluginManager)
    }

    @Bean
    fun pluginDiscoverer(config: TaxiConfig): ExternalPluginProvider {
        val repositories = config.pluginSettings.repositories
                .map { URI.create(it) }
        val pluginCache = config.pluginSettings.localCachePath
        return RemoteExternalPluginProvider(
                repositories, pluginCache, HttpClientBuilder.create().build()
        )
    }

}
