package lang.taxi.packages

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.packages.repository.PackageServiceFactory
import lang.taxi.packages.utils.log
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Paths

class PackageImporterTest {

   @TempDir
   @JvmField
   var folder: File? = null


   @TempDir
   @JvmField
   var mockRepoRoot: File? = null

   @BeforeEach
   fun deployTestRepo() {
      val testRepo = File(Resources.getResource("testRepo").toURI())
      FileUtils.copyDirectory(testRepo, folder)
      log().info("Copied test repo to $folder")
   }

   @Test
   fun given_packageExistsInLocalRepo_then_itIsReturned() {
      val importer = PackageImporter(importerConfig())
      val projectConfig = configWithDependency("taxi/lang.taxi.Dummy/0.2.0")

      val files = importer.fetchDependencies(projectConfig)
      files.should.have.size(2)
   }

   @Test
   fun given_packageDoesNotExistInLocalRepo_then_itIsDownloaded() {
      val importerConfig = importerConfig()
      val (packageService, packageServiceFactory) = fileServiceFactory()
      val downloaderFactory = PackageDownloaderFactory(importerConfig, packageServiceFactory, LogWritingMessageLogger)

      // first, publish the package we're going to depend on:
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0").toURI()
      PackagePublisher(serviceFactory = packageServiceFactory).publish(Paths.get(resource))

      val importer = PackageImporter(importerConfig, downloaderFactory)
      val projectConfig = configWithDependency("taxi/Demo/0.2.0")

      val files = importer.fetchDependencies(projectConfig)
      files.should.have.size(2)
   }

   @Test
   fun `packages declared with a github http endpoint are downloaded`() {
      
   }

   private fun importerConfig(): ImporterConfig {
      return ImporterConfig(
         localCache = folder!!.toPath()
      )
   }

   private fun fileServiceFactory(): Pair<SimpleFilePackageService, PackageServiceFactory> {
      return SimpleFilePackageService.fileServiceFactory(mockRepoRoot!!.toPath())
   }

}

fun configWithDependency(vararg dependencies: String): TaxiPackageProject {
   val deps = dependencies.map { PackageIdentifier.fromId(it) }.asDependencies()
   return TaxiPackageProject(
      name = "taxi/TestProject",
      version = "0.1.0",
      dependencies = deps,
      repositories = listOf(
         Repository("http://localhost:9300")
      )
   )
}
