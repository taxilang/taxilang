package lang.taxi.packages

import com.typesafe.config.Config
import lang.taxi.sources.SourceCode

data class TaxiPackageProject(
   val name: String,
   val version: String,
   val sourceRoot: String = ".",
   val output:String = "dist/",
   val dependencies: Map<String, String> = emptyMap(),
   val repositories: List<Repository> = emptyList(),
   val plugins: Map<String, Config> = emptyMap(),
   val pluginSettings: PluginSettings = PluginSettings(),
   val publishToRepository: Repository? = null
) {
   val identifier: PackageIdentifier = PackageIdentifier(ProjectName.fromId(name), version)
   val dependencyPackages: List<PackageIdentifier> = dependencies.map { (projectId, version) ->
      PackageIdentifier(ProjectName.fromId(projectId), version)
   }
}

data class TaxiPackageSources(val project: TaxiPackageProject, val sources: List<SourceCode>)
