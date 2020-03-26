package lang.taxi.packages

import lang.taxi.packages.utils.log
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.impl.client.HttpClientBuilder
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class PackagePublisher {
   fun publish(projectHome: Path, releaseType: ReleaseType? = null) {
      val packageFile = projectHome.resolve("taxi.conf")
      require(Files.exists(packageFile)) { "Project file $packageFile doesn't exist" }

      val project = TaxiProjectLoader().withConfigFileAt(packageFile).load()
      val publishToRepository = project.publishToRepository
         ?: error("Cannot publish without a publishToRepository defined")

      val zip = createZip(projectHome, project.identifier)

      log().info("Publishing package ${project.identifier.id} from ${zip.canonicalPath} to ${publishToRepository!!.url}")
      val releaseUrlParam = if (releaseType != null) "?releaseType=$releaseType" else "/${project.identifier.version}"

      val name = project.identifier.name
      val url = "${publishToRepository.url}/schemas/${name.organisation}/${name.name}/releases$releaseUrlParam"

      upload(zip, url)
   }

   private fun createZip(projectHome: Path, identifier: PackageIdentifier): File {
      val zipFilePath = FileUtils.getTempDirectory().toPath().resolve("${identifier.fileSafeIdentifier}.zip")
      val zipFile = ZipFile(zipFilePath.toFile())
      projectHome.toFile()
         .walkTopDown()
         .filter { listOf("conf", "taxi").contains(it.extension) }
         .forEach {
            if (it.isFile) {
               log().info("Adding file ${it.canonicalPath}")
               val zipParameters = ZipParameters()
               zipParameters.compressionMethod = Zip4jConstants.COMP_DEFLATE
               zipParameters.compressionLevel = Zip4jConstants.DEFLATE_LEVEL_NORMAL
               zipFile.addFile(it, zipParameters)
            }
         }
      return zipFilePath.toFile()
   }

   private fun upload(fileToRelease: File, url: String) {
      val entity: HttpEntity = MultipartEntityBuilder.create()
         .addPart("file", FileBody(fileToRelease))
         .build()

      val request = HttpPost(url)
      request.entity = entity

      val client: HttpClient = HttpClientBuilder.create().build()
      try {
         val response: HttpResponse = client.execute(request)
         val statusCode = response.statusLine.statusCode
         if (statusCode in 200..299) {
            log().info("Artifact uploaded successfully")
         } else {
            log().error("Failed to upload artifact: ${response.statusLine}, ${IOUtils.toString(response.entity.content, Charset.defaultCharset())}")
         }
      } catch (error: Exception) {
         log().error("Failed to upload", error)
      }
   }
}
