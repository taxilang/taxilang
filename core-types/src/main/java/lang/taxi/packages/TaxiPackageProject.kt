package lang.taxi.packages

import lang.taxi.sources.SourceCode
import java.nio.file.Path


data class TaxiPackageProject(
   val name: String,
   val version: String,
   val sourceRoot: String = ".",
   val dependencies: Map<String, String> = emptyMap(),
   val repositories: List<Repository> = emptyList(),
   val publishToRepository: Repository? = null
) {
   val identifier: PackageIdentifier = PackageIdentifier(ProjectName.fromId(name), version)

   val dependencyPackages: List<PackageIdentifier> = dependencies.map { (projectId, version) ->
      PackageIdentifier(ProjectName.fromId(projectId), version)
   }
}

data class TaxiPackageSources(val project: TaxiPackageProject, val sources:List<SourceCode>)
