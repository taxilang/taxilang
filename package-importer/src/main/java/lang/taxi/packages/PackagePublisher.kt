package lang.taxi.packages

import lang.taxi.packages.repository.DefaultPackageServiceFactory
import lang.taxi.packages.repository.PackageServiceFactory
import lang.taxi.packages.utils.log
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class PackagePublisher(
   private val serviceFactory: PackageServiceFactory = DefaultPackageServiceFactory,
   private val credentials: List<Credentials> = emptyList()
) {
   fun publish(projectHome: Path, releaseType: ReleaseType? = null) {
      val packageFile = projectHome.resolve("taxi.conf")
      require(Files.exists(packageFile)) { "Project file $packageFile doesn't exist" }

      val project = TaxiProjectLoader().withConfigFileAt(packageFile).load()
      val publishToRepository = project.publishToRepository
         ?: error("Cannot publish without a publishToRepository defined")

      val zip = Companion.createZip(projectHome, project.identifier)

      log().info("Publishing package ${project.identifier.id} from ${zip.canonicalPath} to ${publishToRepository!!.url}")
      val releaseUrlParam = if (releaseType != null) "?releaseType=$releaseType" else "/${project.identifier.version}"

      val name = project.identifier.name
      val service = serviceFactory.get(publishToRepository, credentials)
      try {
         val response = service.upload(zip, project)
         val statusCode = response.status.code
         if (statusCode in 200..299) {
            log().info("Artifact uploaded successfully")
         } else {
            log().error("Failed to upload artifact: ${response.status} - ${response.bodyString()}")
         }
      } catch (error: Exception) {
         log().error("Failed to upload", error)
      }

   }

   companion object {
      fun createZip(projectHome: Path, identifier: PackageIdentifier): File {
         val zipFilePath = FileUtils.getTempDirectory().toPath().resolve("${identifier.fileSafeIdentifier}.zip")
         val zipFile = ZipFile(zipFilePath.toFile())
         projectHome.toFile()
            .walkTopDown()
            .filter { listOf("conf", "taxi").contains(it.extension) }
            .forEach {
               if (it.isFile) {
                  log().info("Adding file ${it.canonicalPath}")
                  val zipParameters = ZipParameters()
                  zipParameters.compressionMethod = CompressionMethod.DEFLATE
                  zipParameters.compressionLevel = CompressionLevel.NORMAL
                  zipFile.addFile(it, zipParameters)
               }
            }
         return zipFilePath.toFile()
      }
   }


}
