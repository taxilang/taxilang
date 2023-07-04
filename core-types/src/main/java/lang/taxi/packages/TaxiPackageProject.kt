package lang.taxi.packages

import com.typesafe.config.Config
import lang.taxi.linter.TaxiConfLinterRuleConfig
import lang.taxi.sources.SourceCode
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Path

typealias GlobPattern = String
/**
 * The type of sources found under a specific path. (eg. pipelines, extensions, etc)
 */
typealias SourcesType = String

data class TaxiPackageProject(
   val name: String,
   val version: String,
   val sourceRoot: String = ".",
   val output: String = "dist/",
   val dependencies: Map<String, String> = emptyMap(),
   val repositories: List<Repository> = emptyList(),
   val plugins: Map<String, Config> = emptyMap(),
   val pluginSettings: PluginSettings = PluginSettings(),
   val publishToRepository: Repository? = null,
   val credentials: List<Credentials> = emptyList(),
   val taxiHome: Path = SystemUtils.getUserHome().toPath().resolve(".taxi/"),
   val linter: Map<String, TaxiConfLinterRuleConfig> = emptyMap(),
   val additionalSources: Map<SourcesType, GlobPattern> = emptyMap(),
   val packageRootPath: Path? = null
) {
   val identifier: PackageIdentifier = PackageIdentifier(ProjectName.fromId(name), version)
   val dependencyPackages: List<PackageIdentifier> = dependencies.map { (projectId, version) ->
      PackageIdentifier(ProjectName.fromId(projectId), version)
   }


}

// TODO : We also have PackageSource in the packageImporter.
// This is confusing, one should go away.
data class TaxiPackageSources(val project: TaxiPackageProject, val sources: List<SourceCode>)
