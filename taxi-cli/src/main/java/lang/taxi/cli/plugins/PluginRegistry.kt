package lang.taxi.cli.plugins

import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.readers.SelectReader
import lang.taxi.cli.utils.log
import lang.taxi.plugins.Artifact
import ro.fortsoft.pf4j.DefaultPluginManager
import ro.fortsoft.pf4j.PluginManager
import java.io.File
import kotlin.reflect.KClass

/**
 * This is the Shipman registry of plugins.
 * Responsible for importing and providing the requested plugins at launch time.
 *
 * Note - not to be confused with a PluginManager, which is an external tool, and used
 * as part of Shipman's plugin infrastructure.
 *
 * You should interact with this class, not the plugin manager.
 *
 */
class PluginRegistry(externalPluginProviders: List<ExternalPluginProvider>,
                     internalPlugins: List<InternalPlugin>,
                     requiredPlugins: Map<Artifact, Config>,
                     val pluginManager: PluginManager) {
    val artifacts: Set<Artifact>
    val plugins: List<Plugin>

    // The set of plugins that were explicitly declared.
    // Excludes any internal plugins that are loaded by default,
    // but weren't explicitly declared
    val declaredPlugins: List<Plugin>

    private val externalPluginProvider: CompositeExternalPluginProvider = CompositeExternalPluginProvider(externalPluginProviders)

    init {

        val externalPluginLocations = externalPluginProvider.resolvePlugins(requiredPlugins.keys.toList())
        this.artifacts = externalPluginLocations.keys + internalPlugins.map { it.artifact }

        val externalPlugins = loadRemotePlugins(externalPluginLocations.values)
        this.plugins = externalPlugins + internalPlugins

        assertAllArtifactsSatisfied(requiredPlugins.keys)

        configurePlugins(requiredPlugins)
        log().debug("The following plugins have been loaded: ")
        this.artifacts.forEach { log().debug(it.getIdentifier()) }

        val requiredPluginIds = requiredPlugins.keys.map { it.id }
        declaredPlugins = this.plugins.filter { requiredPluginIds.contains(it.id) }
    }

    private fun configurePlugins(requiredPlugins: Map<Artifact, Config>) {
        this.plugins
                .filterIsInstance<PluginWithConfig<Any>>()
                .forEach { plugin ->
                    val pluginConfig = requiredPlugins.filterKeys { artifact -> artifact.artifactId == plugin.id }.values.first()
                    val pluginSupertype = plugin::class.supertypes.filter { it.classifier == PluginWithConfig::class }.first()
                    val configType = pluginSupertype.arguments[0].type!!.classifier as KClass<*>
                    val typedPluginConfig = SelectReader.extractWithoutPath(ClassContainer(configType), pluginConfig)
                    plugin.setConfig(typedPluginConfig)
                }
    }

    private fun loadRemotePlugins(remotePlugins: Iterable<File>): List<Plugin> {
        remotePlugins.forEach { pluginManager.loadPlugin(it.toPath()) }
        pluginManager.startPlugins()
        return pluginManager.getExtensions(Plugin::class.java)
    }

    companion object {
        // For testing
        @JvmStatic
        fun empty(): PluginRegistry {
            return PluginRegistry(emptyList(), emptyList(), emptyMap(), DefaultPluginManager())
        }
    }

    private fun assertAllArtifactsSatisfied(requiredArtifacts: Iterable<Artifact>) {
        val unsatisfiedArtifacts = requiredArtifacts.filterNot { requiredArtifact -> this.artifacts.any { it.satisfies(requiredArtifact) } }
        if (unsatisfiedArtifacts.isNotEmpty()) {
            throw IllegalStateException("The following plugins were not resolved: " + unsatisfiedArtifacts.joinToString(", "))
        }
    }

}
