package org.taxilang.packagemanager.repository.nexus

import lang.taxi.packages.Credentials
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.Repository
import lang.taxi.packages.TaxiPackageProject
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.taxilang.packagemanager.layout.TaxiArtifactType
import org.taxilang.packagemanager.utils.basicAuth
import org.taxilang.packagemanager.utils.log
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.exists

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
      repository.settings[REPOSITORY_NAME_PARAM_NAME]?.toString()
         ?: error("Expected property $REPOSITORY_NAME_PARAM_NAME to be provided"),
      credentials,
      httpClient
   )

   companion object {
      const val REPOSITORY_TYPE = "nexus"

      /**
       * The config setting within the repository.settings block inside a taxi conf file
       */
      const val REPOSITORY_NAME_PARAM_NAME = "repositoryName"
      fun filename(identifier: PackageIdentifier, artifactType: TaxiArtifactType): String {
         return when (artifactType) {
            TaxiArtifactType.TAXI_CONF_FILE -> "taxi.conf"
            TaxiArtifactType.TAXI_PROJECT_BUNDLE -> "${identifier.fileSafeIdentifier}.zip"
         }
      }

      fun url(baseUrl: String, repositoryName: String, identifier: PackageIdentifier, type: TaxiArtifactType): String {
         val filename = filename(identifier, type)
         return "${baseUrl.removeSuffix("/")}/repository/$repositoryName/${identifier.name.organisation}/${identifier.name.name}/${identifier.version}/$filename"
      }

   }

   override fun upload(zip: File, taxiConfFilePath: Path, project: TaxiPackageProject): Response {
      require(project.packageRootPath != null) { "Cannot upload project when packageRootPath is not set. This should be set by tooling (not users), so indicates a bug" }
      val taxiConfFile = project.packageRootPath!!.resolve("taxi.conf")
      require(taxiConfFile.exists()) { "Cannot upload project, as not taxi.conf file found at $taxiConfFile" }

      val zipUrl = url(nexusUrl, repositoryName, project.identifier, TaxiArtifactType.TAXI_PROJECT_BUNDLE)

      val zipUploadRequest = Request(Method.PUT, zipUrl)
         .body(zip.inputStream())
         .basicAuth(credentials)

      val zipUploadResponse = httpClient(zipUploadRequest)
      return if (zipUploadResponse.status.successful) {
         val taxiConfUrl = url(nexusUrl, repositoryName, project.identifier, TaxiArtifactType.TAXI_CONF_FILE)
         val taxiConfRequest = Request(Method.PUT, taxiConfUrl)
            .body(taxiConfFile.toFile().inputStream())
            .basicAuth(credentials)
         httpClient(taxiConfRequest)
      } else {
         zipUploadResponse
      }
   }

   override fun attemptDownload(identifier: PackageIdentifier): InputStream? {
      val name = identifier.name
      val url = "${nexusUrl}/repository/$repositoryName/${name.organisation}/${name.name}/${identifier.version}/${
         filename(identifier, TaxiArtifactType.TAXI_PROJECT_BUNDLE)
      }"
      log().info("Attempting to download ${identifier.id} from $url")

      val request = Request(Method.GET, url)
      val response = httpClient(request)
      return if (response.status.code in 200..299) {
         response.body.stream
      } else {
         log().info("Couldn't download from $url - got ${response.status}")
         null
      }
   }

   override val repositoryType: String = REPOSITORY_TYPE
}
