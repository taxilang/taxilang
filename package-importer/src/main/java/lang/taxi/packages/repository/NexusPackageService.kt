package lang.taxi.packages.repository

import lang.taxi.packages.Credentials
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.Repository
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.utils.basicAuth
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.io.File
import java.io.InputStream

class NexusPackageService(
   val nexusUrl: String,
   val repositoryName: String,
   private val credentials: Credentials? = null,
   private val httpClient: HttpHandler = ApacheClient()
) : PackageService {
   constructor(
      repository: Repository,
      credentials: Credentials?,
      httpClient: HttpHandler = ApacheClient()
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

   override fun upload(zip: File, project: TaxiPackageProject): Response {
//      val entity: HttpEntity = FileEntity(zip)
      val projectName = project.identifier.name
      val version = project.version
      val url =
         "$nexusUrl/repository/$repositoryName/${projectName.organisation}/${projectName.name}/$version/${
            filename(
               project.identifier
            )
         }"
      val request = Request(Method.PUT, url)
         .body(zip.inputStream())
         .basicAuth(credentials)

      return httpClient(request)
   }

   private fun filename(identifier: PackageIdentifier): String {
      return "${identifier.fileSafeIdentifier}.zip"
   }

   override fun attemptDownload(identifier: PackageIdentifier): InputStream? {
      val name = identifier.name
      val url = "${nexusUrl}/repository/$repositoryName/${name.organisation}/${name.name}/${identifier.version}/${
         filename(identifier)
      }"
//      userFacingLogger.info("Attempting to download ${identifier.id} from $url")

      val request = Request(Method.GET, url)
      val response = httpClient(request)
      return if (response.status.code in 200..299) {
         response.body.stream
      } else {
//         userFacingLogger.info("Couldn't download from $url - got ${response.status}")
         null
      }
   }

   override val repositoryType: String = REPOSITORY_TYPE
}
