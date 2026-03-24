package lang.taxi.types

import com.google.common.cache.CacheBuilder
import lang.taxi.packages.PackageIdentifier
import java.io.File
import java.lang.Exception
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

object WorkspaceUri {
   const val PREFIX = "workspace://"
   /**
    * Generates a uri in a custom workspace uri format, containing the packageIdentifier.
    * This is preferred to using the path property, as it doesn't leak physical disk location,
    * but is still unique
    *
    * eg: workspace://com.foo.acme:myProject:1.0.0/queries/foo.taxi
    */
   fun toWorkspaceUri(packageIdentifier: PackageIdentifier?, name: String): String? {
      return if (packageIdentifier != null) {
         // Convert to Taxi PackageIdentifier
         "$PREFIX${packageIdentifier.uriSafeId}/$name"
      } else null
   }

   fun splitWorkspaceUri(workspaceUri: String):Pair<PackageIdentifier, String> {
      val uri = URI.create(workspaceUri)
      val packageIdentifier = PackageIdentifier.fromUriSafeId(uri.authority)
      return packageIdentifier to uri.path.removePrefix("/")
   }
}

object SourceNames {
   private val sourceNameCache = CacheBuilder.newBuilder()
      .build<String, String>()

   // TODO : This is not tested yet, but want to encapsulate the logic to
   // a single location
   fun normalize(uri: URI): String {
      return normalize(uri.toASCIIString())
   }

   // TODO : This is not tested yet, but want to encapsulate the logic to
   // a single location
   fun normalize(path: Path): String {
      return normalize(path.toString())
   }

   /**
    * Attempts to normalize and return the path portion of a Source file name.
    * If a URI parsing exception occurs, will simply return as-is
    */
   fun normalize(sourceName: String): String {
      return sourceNameCache.get(sourceName) {
         if (!sourceName.contains("/") && !sourceName.contains("""\""")) {
            // This isn't a filename or path - it's probably one of the 'unknown path' markers
            sourceName
         } else {
            tryParseAsInMemory(sourceName)
               ?: tryParseAsWorkspaceUri(sourceName)
               ?: tryParseAsUri(sourceName)
               ?: tryParseAsFile(sourceName)
               ?: tryParseAsPath(sourceName)
               ?: sourceName
         }
      }
   }

   private fun tryParseAsWorkspaceUri(sourceName: String): String? {
      return if (sourceName.startsWith(WorkspaceUri.PREFIX)) {
         sourceName
      } else null
   }
   // When usig the in-memory LSP, source URI's are passed as
   // inmemory:// ....
   private fun tryParseAsInMemory(sourceName: String): String? {
      return try {
         return when {
            sourceName.startsWith("inmemory:") -> sourceName
            sourceName.startsWith("vscode-notebook-cell:") -> sourceName
            sourceName.startsWith("file:///web/sandbox") -> sourceName
            else -> null
         }
      } catch(e:Exception) {
         null
      }
   }

   private fun tryParseAsFile(sourceName: String): String? {
      return try {
         File(sourceName).toPath().toAbsolutePath().toUri().toString()
      } catch(e:Exception) {
         return null
      }

   }

   private fun tryParseAsUri(sourceName: String): String? {
      return try {
         // Wrapping URI.create() in Paths.get() ensures that the URI contains a file:///
         // prefix, which on windows seems to get dropped
         Paths.get(URI.create(sourceName)).toUri().toString()
      } catch (e: Exception) {
         return null
      }
   }

   private fun tryParseAsPath(sourceName: String): String? {
      return try {
         Paths.get(sourceName).toUri().toString()
      } catch (e: Exception) {
         return null
      }
   }
}
