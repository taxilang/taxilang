package lang.taxi.packages

import lang.taxi.packages.utils.log
import net.lingala.zip4j.core.ZipFile
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path

class PackageDownloaderFactory(private val importerConfig: ImporterConfig) {
   fun create(projectConfig: ProjectConfig): PackageDownloader = PackageDownloader(importerConfig, projectConfig)
}

class PackageDownloader(val config: ImporterConfig, val projectConfig: ProjectConfig) {
   fun download(identifier: PackageIdentifier): Boolean {
      var downloaded = false
      projectConfig.repositories
         .forEach { repository ->
            if (!downloaded) {
               downloaded = attemptDownload(repository, identifier, config.localCache)
            }
         }
      return downloaded
   }

   private fun attemptDownload(repository: Repository, identifier: PackageIdentifier, localCache: Path): Boolean {
      val client = HttpClientBuilder.create().build()
      val name = identifier.name
      val url = "${repository.url}/schemas/${name.organisation}/${name.name}/releases/${identifier.version}/content"
      log().info("Attempting to download ${identifier.id} from $url")
      val request = RequestBuilder.get(url)
         .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_OCTET_STREAM.mimeType)
         .build()
      val response = client.execute(request)
      return if (response.statusLine.statusCode in 200..299) {
         saveAndUnzip(response.entity, localCache, identifier)
         true
      } else {
         log().info("Couldn't download from $url - got ${response.statusLine}")
         false
      }
   }

   private fun saveAndUnzip(entity: HttpEntity, localCache: Path, identifier: PackageIdentifier) {
      val file = File.createTempFile(identifier.id, ".zip")

      FileOutputStream(file).use { fileStream ->
         IOUtils.copy(entity.content, fileStream)
      }
      log().info("Downloaded ${identifier.id} to temp location ${file.canonicalPath}")
      val path = identifier.localFolder(localCache).toFile()
      FileUtils.forceMkdir(path)
      ZipFile(file).extractAll(path.canonicalPath)
      log().info("Extracted ${identifier.id} to ${path.canonicalPath}")
   }

}
