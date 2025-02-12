package lang.taxi.lsp.sourceService

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


object FileWorkspaceProjectFinder {
   /**
    * Searches under a provided root path for all taxi.conf files.
    * Once a file is found, it's subdirectories are not traversed.
    */

   fun findTaxiConfFiles(root: Path): List<Path> {
      if (!root.exists() || !root.isDirectory()) {
         return emptyList()
      }

      val results = mutableListOf<Path>()

      Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
         override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            // If this directory has a taxi.conf, add it to results and skip traversing deeper
            if (dir.resolve("taxi.conf").exists()) {
               results.add(dir.resolve("taxi.conf"))
               return FileVisitResult.SKIP_SUBTREE
            }
            return FileVisitResult.CONTINUE
         }

         override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            return FileVisitResult.CONTINUE
         }
      })

      return results
   }


}
