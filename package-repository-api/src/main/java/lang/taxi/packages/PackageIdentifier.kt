package lang.taxi.packages

import com.github.zafarkhaja.semver.Version

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
      val UNSPECIFIED = PackageIdentifier(ProjectName("*","*"),"0.0.0")
   }
}

fun List<PackageIdentifier>.asDependencies(): Map<String, String> {
   return this.map { it.name.id to it.version.toString() }.toMap()
}

