package lang.taxi.packages.repository

import lang.taxi.packages.Credentials
import lang.taxi.packages.LogWritingMessageLogger
import lang.taxi.packages.MessageLogger
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.Repository
import lang.taxi.packages.TaxiPackageProject
import org.apache.http.HttpResponse
import java.io.File
import java.io.InputStream

/**
 * Represents a service for inteeracting with a remote package service,
 * such as TaxiHub or Nexus.
 * Responsible for donwload/upload of packages
 */
interface PackageService {
   fun upload(zip: File, project: TaxiPackageProject): HttpResponse
   fun attemptDownload(identifier: PackageIdentifier, userFacingLogger:MessageLogger = LogWritingMessageLogger): InputStream?

   val repositoryType: String
}

/**
 * Responsible for creating the correct PackageService with credentials to access the
 * requested Repository
 */
interface PackageServiceFactory {
   fun get(repository: Repository, credentials: List<Credentials>): PackageService
}

/**
 * Always returns the same service
 */
class SingleServiceFactory(private val service: PackageService) : PackageServiceFactory {
   override fun get(repository: Repository, credentials: List<Credentials>): PackageService {
      return service
   }

}

object DefaultPackageServiceFactory : PackageServiceFactory {
   private val knownRepositoryTypes = listOf(
      NexusPackageService.REPOSITORY_TYPE,
      TaxiHubService.REPOSITORY_TYPE
   )

   override fun get(repository: Repository, credentials: List<Credentials>): PackageService {
      val repositoryCredentials = credentials.firstOrNull { it.repositoryName == repository.name }
      return when (repository.type) {
         NexusPackageService.REPOSITORY_TYPE -> NexusPackageService(repository, repositoryCredentials)
         else -> error("Unknown type of repository : ${repository.type} - expected one of ${knownRepositoryTypes.joinToString()}")
      }
   }

}
