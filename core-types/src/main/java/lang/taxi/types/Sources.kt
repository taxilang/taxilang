package lang.taxi.types

import com.google.common.cache.CacheBuilder
import java.io.File
import java.lang.Exception
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

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
               ?: tryParseAsUri(sourceName)
               ?: tryParseAsFile(sourceName)
               ?: tryParseAsPath(sourceName)
               ?: sourceName
         }
      }
   }

   // When usig the in-memory LSP, source URI's are passed as
   // inmemory:// ....
   private fun tryParseAsInMemory(sourceName: String): String? {
      return try {
         if(sourceName.startsWith("inmemory:")) sourceName else null
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
