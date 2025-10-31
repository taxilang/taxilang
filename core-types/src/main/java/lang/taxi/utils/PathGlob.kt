package lang.taxi.utils

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

data class PathGlob(val basePath: Path, val glob: String) {
   companion object {

      //  Expands a glob pattern containing `**` to also match files in the base directory.
      //
      //  Java's glob matcher treats `**` as "zero or more directories", but in practice
      //  `**/*.txt` only matches files in subdirectories, not in the current directory.
      // This function expands such patterns to also include direct matches.
      //
      // Examples:
      // - `**/*.txt` → `{*.txt,**/*.txt}` (matches both `file.txt` and `dir/file.txt`)
      // - `src/**/*.java` → `{src/*.java,src/**/*.java}` (matches both `src/A.java` and `src/pkg/B.java`)
      // - `**/*{.txt,.md}` → `{*.txt,**/*.txt,*.md,**/*.md}` (expands brace groups to avoid nesting)
      // - `wsdl/*.xsd` → `wsdl/*.xsd` (no `**`, returned unchanged)
      //
      // @param glob The glob pattern to expand
      // @return An expanded glob pattern that matches files in both the base directory and subdirectories
      fun expandGlobWithBaseDirectory(glob: String): String {
         // If the glob doesn't contain **, return it as-is
         if (!glob.contains("**")) {
            return glob
         }

         // Extract the base directory (everything before **)
         val baseDir = if (glob.startsWith("**")) {
            ""
         } else {
            glob.substringBefore("**").trimEnd('/')
         }

         // Extract the pattern after **
         val patternAfterDoubleStar = glob.substringAfter("**").removePrefix("/")

         // If the pattern contains braces, we need to expand manually
         if (patternAfterDoubleStar.contains('{')) {
            // Extract the brace content: "*{.txt,.md}" -> ".txt,.md"
            val braceContent = patternAfterDoubleStar.substringAfter('{').substringBefore('}')
            val prefix = patternAfterDoubleStar.substringBefore('{')
            val suffix = patternAfterDoubleStar.substringAfter('}')

            // Split the brace content and create individual patterns
            val options = braceContent.split(',')
            val expandedPatterns = options.flatMap { option ->
               val pattern = "$prefix$option$suffix"
               listOf(
                  if (baseDir.isEmpty()) pattern else "$baseDir/$pattern",  // base dir version
                  if (baseDir.isEmpty()) "**/$pattern" else "$baseDir/**/$pattern"  // recursive version
               )
            }

            return expandedPatterns.joinToString(",", prefix = "{", postfix = "}")
         }

         // No braces, simple expansion
         val baseFilesGlob = if (baseDir.isEmpty()) {
            patternAfterDoubleStar
         } else {
            "$baseDir/$patternAfterDoubleStar"
         }

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
