package org.taxilang.packagemanager.repository.nexus

import lang.taxi.packages.Credentials
import lang.taxi.packages.ReleaseType
import lang.taxi.packages.TaxiProjectLoader
import lang.taxi.utils.log
import org.taxilang.packagemanager.TaxiPackageBundler
import java.nio.file.Files
import java.nio.file.Path

class PackagePublisher(
   private val serviceFactory: PackageServiceFactory = DefaultPackageServiceFactory,
   private val credentials: List<Credentials> = emptyList()
) {
   // TODO: Can we remove releaseType?
   fun publish(projectBasePath: Path, releaseType: ReleaseType? = null) {
      val packageFile = projectBasePath.resolve("taxi.conf")
      require(Files.exists(packageFile)) { "Project file $packageFile doesn't exist" }

      val project =
         TaxiProjectLoader(packageFile).load()
      val publishToRepository = project.publishToRepository
         ?: error("Cannot publish without a publishToRepository defined")

      val bundle = TaxiPackageBundler.createBundle(projectBasePath, project.identifier)

      log().info("Publishing package ${project.identifier.id} from ${bundle.zip.toAbsolutePath()} to ${publishToRepository.url}")

      val service = serviceFactory.get(publishToRepository, credentials)
      try {
         val response = service.upload(bundle.zip.toFile(), bundle.taxiConfFile, project)
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
}
