package lang.taxi.packages

import lang.taxi.packages.repository.DefaultPackageServiceFactory
import lang.taxi.packages.repository.PackageServiceFactory
import lang.taxi.packages.utils.log
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path

class PackageDownloaderFactory(private val importerConfig: ImporterConfig, private val serviceFactory:PackageServiceFactory = DefaultPackageServiceFactory) {
   fun create(projectConfig: TaxiPackageProject): PackageDownloader = PackageDownloader(importerConfig, projectConfig, serviceFactory)
}

class PackageDownloader(
   private val downloadPath: Path,
   private val repositories: List<Repository>,
   private val credentials: List<Credentials>,
   private val packageServiceFactory: PackageServiceFactory = DefaultPackageServiceFactory
) {
   constructor(
      config: ImporterConfig,
      projectConfig: TaxiPackageProject,
      packageServiceFactory: PackageServiceFactory = DefaultPackageServiceFactory
   ) : this(
      config.localCache,
      projectConfig.repositories,
      projectConfig.credentials,
      packageServiceFactory
   )

   fun download(identifier: PackageIdentifier): Boolean {
      val repositoryUsedForDownload = repositories
         .asSequence()
         .filter { repository ->
            attemptDownload(repository, identifier, downloadPath)
         }
         .firstOrNull()

      return repositoryUsedForDownload != null
   }

   private fun attemptDownload(repository: Repository, identifier: PackageIdentifier, localCache: Path): Boolean {
      val packageRepository = packageServiceFactory.get(repository, credentials)
      val response = packageRepository.attemptDownload(identifier)
      return if (response != null) {
         saveAndUnzip(response, localCache, identifier)
         true
      } else {
         false
      }

   }

   private fun saveAndUnzip(inputStream: InputStream, localCache: Path, identifier: PackageIdentifier) {
      val file = File.createTempFile(identifier.fileSafeIdentifier, ".zip")

      FileOutputStream(file).use { fileStream ->
         IOUtils.copy(inputStream, fileStream)
      }
      log().info("Downloaded ${identifier.id} to temp location ${file.canonicalPath}")
      val path = identifier.localFolder(localCache).toFile()
      FileUtils.forceMkdir(path)
      ZipFile(file).extractAll(path.canonicalPath)
      log().info("Extracted ${identifier.id} to ${path.canonicalPath}")
   }

   private fun saveAndUnzip(entity: HttpEntity, localCache: Path, identifier: PackageIdentifier) {
      return saveAndUnzip(entity.content, localCache, identifier)

   }

}
