package lang.taxi.cli

import lang.taxi.cli.config.TaxiConfig
import lang.taxi.cli.plugins.ExternalPluginProvider
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.RemoteExternalPluginProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.pf4j.DefaultPluginManager
import org.pf4j.PluginClasspath
import org.pf4j.PluginManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.nio.file.Path

@Configuration
class PluginConfig {

    // Externalized to a bean so that other scnearios can inject their own pluginMAnager,
    // where there are different classloader requirements
    // (eg., in TaxiHub / Forge, where there are runtime bindings to the
    // application classloader, so need to load pluigins into the app classloader
    // to avoid methodNotFound exceptions)
    @Bean
    fun pluginManager(config: TaxiConfig): PluginManager {
        val pluginDirectory = config.pluginSettings.localCachePath.toPath()
        return DefaultPluginManager(pluginDirectory);
    }

    @Bean
    fun pluginRegistry(externalPluginProviders: List<ExternalPluginProvider>, internalPlugins: List<InternalPlugin>, config: TaxiConfig, pluginManager: PluginManager): PluginRegistry {
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

