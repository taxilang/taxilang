package lang.taxi.packages

import com.github.zafarkhaja.semver.Version

// TODO : Credentials
data class Repository(
   val url: String,
   val name: String? = null
)

// TODO : This may be redundant.  Leaving it here
// in case it needs to expand to include the files in the pacakge
data class Package(
   val name: PackageIdentifier
) {

}


data class ProjectName(
   val organisation: String,
   val name: String
) {
   val id: String = "$organisation/$name"

   companion object {
      fun fromId(id: String): ProjectName {
         val parts = id.split("/")
         require(parts.size == 2) { "Invalid project id. $id should be in the form of organisation/name" }
         val (organisation, name) = parts
         return ProjectName(organisation, name)
      }
   }
}

data class ProjectConfig(
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
