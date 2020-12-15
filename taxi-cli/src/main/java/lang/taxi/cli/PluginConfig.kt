package lang.taxi.cli

import com.typesafe.config.Config
import lang.taxi.cli.plugins.ExternalPluginProvider
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.cli.plugins.NoOpPluginProvider
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.plugins.RemoteExternalPluginProvider
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.plugins.Artifact
import org.apache.http.impl.client.HttpClientBuilder
import org.pf4j.DefaultPluginManager
import org.pf4j.PluginManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@Configuration
class PluginConfig {

   // Externalized to a bean so that other scnearios can inject their own pluginMAnager,
   // where there are different classloader requirements
   // (eg., in TaxiHub / Forge, where there are runtime bindings to the
   // application classloader, so need to load pluigins into the app classloader
   // to avoid methodNotFound exceptions)
   @Bean
   fun pluginManager(environment: TaxiEnvironment): PluginManager {
      val pluginDirectory = when (environment) {
         is TaxiProjectEnvironment -> environment.project.pluginSettings.localCachePath.toPath()
         else -> environment.projectRoot
      }
      return DefaultPluginManager(pluginDirectory);
   }

   @Bean
   fun pluginRegistry(externalPluginProviders: List<ExternalPluginProvider>, internalPlugins: List<InternalPlugin>, environment: TaxiEnvironment, pluginManager: PluginManager): PluginRegistry {
      val pluginArtifacts = when (environment) {
         is TaxiProjectEnvironment -> environment.project.pluginArtifacts()
         else -> emptyMap()
      }
      return PluginRegistry(externalPluginProviders, internalPlugins, pluginArtifacts, pluginManager)
   }

   @Bean
   fun pluginDiscoverer(project: TaxiPackageProject?): ExternalPluginProvider {
      return if (project == null) {
         NoOpPluginProvider()
      } else {
         val repositories = project.pluginSettings.repositories
            .map { URI.create(it) }
         val pluginCache = project.pluginSettings.localCachePath
         return RemoteExternalPluginProvider(
            repositories, pluginCache, HttpClientBuilder.create().build()
         )
      }
   }
}

fun TaxiPackageProject.pluginArtifacts(): Map<Artifact, Config> {
   return this.plugins.mapKeys { (key, _) -> Artifact.parse(key) }
}
