package lang.taxi.utils

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

data class PathGlob(val basePath: Path, val glob: String) {
   companion object {
      fun expandGlobWithBaseDirectory(glob: String): String {
         // Check if the glob starts with "**", indicating no specific base directory
         val baseDir = if (glob.startsWith("**")) "." else glob.substringBefore("**").trimEnd('/')
         // Generate the pattern for files directly in the base directory
         val baseFilesGlob = if (baseDir == ".") "*.${glob.substringAfterLast('.')}" else "$baseDir/*.${glob.substringAfterLast('.')}"
         // Combine the original and base directory globs into a single string
         return "{$baseFilesGlob,$glob}"
      }
   }


   fun <T> mapEachDirectoryEntry(action: (Path) -> T): Map<Path, T> {
      val result = mutableMapOf<Path, T>()
      val expandedGlob = expandGlobWithBaseDirectory(glob)

      val pathMatcher = FileSystems.getDefault().getPathMatcher("glob:$expandedGlob")

      Files.walk(basePath)
         .filter { path ->
            val relativePath = basePath.relativize(path)
            // Skip 'node_modules' directory
            if (relativePath.toString().contains("node_modules")) {
               return@filter false
            }
            val matches = pathMatcher.matches(relativePath)
            matches
         }
         .forEach { path ->
            try {
               result[path] = action(path)
            } catch (e: Exception) {
               log().error("Failed to process path at $path: ${e.message}", e)
            }

         }
      return result
   }
}


fun File.glob(glob: String): PathGlob {
   return PathGlob(this.toPath(), glob)
}

fun Path.glob(glob: String): PathGlob = PathGlob(this, glob)
