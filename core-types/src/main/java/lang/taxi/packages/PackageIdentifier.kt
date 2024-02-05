package lang.taxi.packages

import com.github.zafarkhaja.semver.Version

data class PackageIdentifier(val name: ProjectName, val version: String) {

   val id = "${name.id}/$version"

   val fileSafeIdentifier = "${name.name}-$version"
   val semver: Version? = try {
      Version.valueOf(version)
   } catch (e: Exception) {
      null
   }

   companion object {
      fun orgName(id: String): String = id.split("/")[0]
      fun projectName(id: String): String = id.split("/")[1]
      fun withoutVersion(id: String): String = id.split("/").slice(0..1)
         .joinToString("/")

      /**
       * Returns a version-ish value from a packageId.
       * The version may be a git Url (or similar), if it indicates a remote
       * package that hasn't yet been downloaded
       */
      fun versionIsh(id: String): String = id.split("/")
         .drop(2)
         .joinToString("/")

      fun fromId(id: String): PackageIdentifier {
         val parts = id.split("/")
         require(parts.size == 3) { "Expected a name in the format organisation/name/version" }
         val (organisation, name, version) = parts
         return PackageIdentifier(
            name = ProjectName(organisation, name),
            version = version
         )
      }

      val UNSPECIFIED = PackageIdentifier(ProjectName("*", "*"), "0.0.0")
   }
}

fun List<PackageIdentifier>.asDependencies(): Map<String, String> {
   return this.map { it.name.id to it.version.toString() }.toMap()
}

