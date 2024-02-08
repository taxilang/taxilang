package org.taxilang.packagemanager

import lang.taxi.packages.PackageIdentifier
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

object TaxiPackageBundler {
   const val EXTENSION = "taxi.zip"
   fun createBundle(projectHome: Path, identifier: PackageIdentifier): TaxiProjectBundle {
      val tempDir = createTempDirectory()
      val zip = createZip(projectHome, identifier, tempDir)
      val taxiConf = projectHome.resolve("taxi.conf")
      require(Files.exists(taxiConf)) { "Expected a taxi.conf file at $taxiConf" }
      return TaxiProjectBundle(
         taxiConf,
         zip.toPath()
      )
   }

   private fun createZip(
      projectHome: Path,
      identifier: PackageIdentifier,
      tempDir: Path,
      extension: String = ".$EXTENSION"
   ): File {
      val zipFilePath = tempDir.resolve("${identifier.fileSafeIdentifier}$extension")
      val zipFile = ZipFile(zipFilePath.toFile())
      projectHome.toFile()
         .walkTopDown()
         .filter { listOf("conf", "taxi").contains(it.extension) }
         .forEach { file ->
            if (file.isFile) {
               val zipParameters = ZipParameters()
               zipParameters.compressionMethod = CompressionMethod.DEFLATE
               zipParameters.compressionLevel = CompressionLevel.NORMAL

               val relativePath = projectHome.relativize(file.toPath()).toString()
               zipParameters.fileNameInZip = relativePath
               zipFile.addFile(file, zipParameters)
            }
         }
      return zipFilePath.toFile()
   }
}

data class TaxiProjectBundle(
   val taxiConfFile: Path,
   val zip: Path
) {
   fun copyTo(path: Path) {
      if (taxiConfFile.parent != path) {
         Files.copy(taxiConfFile, path.resolve(taxiConfFile.fileName))
      }
      if (zip.parent != path) {
         Files.copy(zip, path.resolve(zip.fileName))
      }

   }
}
