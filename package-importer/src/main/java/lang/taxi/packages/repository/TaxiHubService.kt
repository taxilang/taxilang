package lang.taxi.packages.repository

import lang.taxi.packages.MessageLogger
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.TaxiPackageProject
import org.http4k.core.Response
import java.io.File
import java.io.InputStream

// NOte : I've moved this out of PackagePublisher, as part of getting support for Nexus working.
// Haven't tested this, need to decide if we want TaxiHub to be an actual thing.
// Also, some changes have been made which are not yet reflected in taxihub proejct.
class TaxiHubService(
   private val baseUrl: String
) : PackageService {
   companion object {
      const val REPOSITORY_TYPE = "taxi-hub"
   }

   private fun upload(fileToRelease: File, url: String): Response {
//      val entity: HttpEntity = MultipartEntityBuilder.create()
//         .addPart("file", FileBody(fileToRelease))
//         .build()
//
//      val request = HttpPost(url)
//      request.entity = entity
//
//      val client: HttpClient = HttpClientBuilder.create().build()
//      try {
//         val response: HttpResponse = client.execute(request)
//         val statusCode = response.statusLine.statusCode
//         if (statusCode in 200..299) {
//            log().info("Artifact uploaded successfully")
//         } else {
//            log().error(
//               "Failed to upload artifact: ${response.statusLine}, ${
//                  IOUtils.toString(
//                     response.entity.content,
//                     Charset.defaultCharset()
//                  )
//               }"
//            )
//         }
//      } catch (error: Exception) {
//         log().error("Failed to upload", error)
//      }
      TODO("Refactor to return http4k response")
   }

   override fun upload(zip: File, project: TaxiPackageProject): Response {
      val projectName = project.identifier.name
//      val url = "${baseUrl}/schemas/${projectName.organisation}/${projectName.name}/releases$releaseUrlParam"
      // Note - have changed this from having taxihub decide the version, to having the client suggest it.
      // Makes the api cleaner for other repository types.
      // Need to update taxi-hub project to reflect this cahgne.
      val url =
         "${baseUrl}/schemas/${projectName.organisation}/${projectName.name}/releases/${project.identifier.version.toString()}"

      return upload(zip, url)
   }

   override fun attemptDownload(identifier: PackageIdentifier, userFacingLogger:MessageLogger): InputStream? {
      TODO("Not yet re-implemented")
//      val client = HttpClientBuilder.create().build()
//      val name = identifier.name
//      val url = "${repository.url}/schemas/${name.organisation}/${name.name}/releases/${identifier.version}/content"
//      log().info("Attempting to download ${identifier.id} from $url")
//      val request = RequestBuilder.get(url)
//         .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_OCTET_STREAM.mimeType)
//         .build()
//      val response = client.execute(request)
//      return if (response.statusLine.statusCode in 200..299) {
//         saveAndUnzip(response.entity, localCache, identifier)
//         true
//      } else {
//         log().info("Couldn't download from $url - got ${response.statusLine}")
//         false
//      }
   }

   override val repositoryType: String = REPOSITORY_TYPE
}
