package lang.taxi.packages.repository

import lang.taxi.packages.Credentials
import lang.taxi.packages.MessageLogger
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.Repository
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.utils.log
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.FileEntity
import org.apache.http.impl.client.HttpClientBuilder
import java.io.File
import java.io.InputStream

class NexusPackageService(
   private val nexusUrl: String,
   private val repositoryName: String,
   private val credentials: Credentials? = null,
   private val httpClient: HttpClient = HttpClientBuilder.create().build()
) : PackageService {
   constructor(
      repository: Repository,
      credentials: Credentials?,
      httpClient: HttpClient = HttpClientBuilder.create().build()
   ) : this(
      repository.url,
      repository.settings[Settings.REPOSITORY_NAME]?.toString()
         ?: error("Expected property ${Settings.REPOSITORY_NAME} to be provided"),
      credentials,
      httpClient
   )

   companion object {
      const val REPOSITORY_TYPE = "nexus"

      object Settings {
         const val REPOSITORY_NAME = "repositoryName"
      }

   }

   override fun upload(zip: File, project: TaxiPackageProject): HttpResponse {
      val entity: HttpEntity = FileEntity(zip)
      val projectName = project.identifier.name
      val version = project.version
      val url =
         "$nexusUrl/repository/$repositoryName/${projectName.organisation}/${projectName.name}/$version/${
            filename(
               project.identifier
            )
         }"


      val request = HttpPut(url)
      request.entity = entity
      if (credentials != null) {
         request.addHeader(HttpHeaders.AUTHORIZATION, credentials.asBasicAuthHeader())
         log().info("Will attempt to publish to $url using basic auth credentials supplied")
      } else {
         log().info("Will attempt to publish to $url without any credentials")
      }


      return httpClient.execute(request)
   }

   private fun filename(identifier: PackageIdentifier): String {
      return "${identifier.fileSafeIdentifier}.zip"
   }

   override fun attemptDownload(identifier: PackageIdentifier, userFacingLogger:MessageLogger): InputStream? {
      val name = identifier.name
      val url = "${nexusUrl}/repository/$repositoryName/${name.organisation}/${name.name}/${identifier.version}/${
         filename(identifier)
      }"
      userFacingLogger.info("Attempting to download ${identifier.id} from $url")
      val request = RequestBuilder.get(url)
         .build()
      val response = httpClient.execute(request)
      return if (response.statusLine.statusCode in 200..299) {
         response.entity.content
      } else {
         userFacingLogger.info("Couldn't download from $url - got ${response.statusLine}")
         null
      }
   }

   override val repositoryType: String = REPOSITORY_TYPE
}
