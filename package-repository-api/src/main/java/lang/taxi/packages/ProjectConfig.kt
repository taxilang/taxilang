package lang.taxi.packages

import com.github.zafarkhaja.semver.Version

// TODO : Credentials
data class Repository(
   val url: String,
   val name: String? = null
)

data class PackageIdentifier(val name: ProjectName, val version: Version) {
   constructor(name: ProjectName, version: String) : this(name, Version.valueOf(version))

   val id = "${name.id}/$version"

   val fileSafeIdentifier = "${name.organisation}.${name.name}.$version"

   companion object {
      fun fromId(id: String): PackageIdentifier {
         val parts = id.split("/")
         require(parts.size == 3) { "Expected a name in the format organisation/name/version" }
         val (organisation, name, version) = parts
         return PackageIdentifier(
            name = ProjectName(organisation, name),
            version = version
         )
      }
   }
}

fun List<PackageIdentifier>.asDependencies(): Map<String, String> {
   return this.map { it.name.id to it.version.toString() }.toMap()
}

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
   val dependencies: Map<String, String> = emptyMap(),
   val repositories: List<Repository> = emptyList(),
   val publishToRepository: Repository? = null
) {
   val identifier: PackageIdentifier = PackageIdentifier(ProjectName.fromId(name), version)

   val dependencyPackages: List<PackageIdentifier> = dependencies.map { (projectId, version) ->
      PackageIdentifier(ProjectName.fromId(projectId), version)
   }
}
