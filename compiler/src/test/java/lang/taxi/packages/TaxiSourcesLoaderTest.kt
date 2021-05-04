package lang.taxi.packages

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.messages.Severity
import lang.taxi.packages.SimpleFilePackageService.Companion.fileServiceFactory
import lang.taxi.packages.repository.PackageServiceFactory
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths

class TaxiSourcesLoaderTest {

   @Rule
   @JvmField
   val localCache = TemporaryFolder()

   @Rule
   @JvmField
   val mockRepoRoot = TemporaryFolder()
   private lateinit var packageService: SimpleFilePackageService
   private lateinit var packageServiceFactory: PackageServiceFactory
   private lateinit var downloaderFactory: PackageDownloaderFactory

   @Before
   fun setup() {
      val (packageService, packageServiceFactory) = fileServiceFactory(mockRepoRoot.root.toPath())
      this.packageService = packageService
      this.packageServiceFactory = packageServiceFactory
      this.downloaderFactory = PackageDownloaderFactory(importerConfig(),packageServiceFactory, LogWritingMessageLogger)
   }

   @Test
   fun `will load sources of project with dependencies`() {
      // First, set up a temp repository and publish
      val resource = Resources.getResource("sample-project").toURI()
      PackagePublisher(packageServiceFactory).publish(Paths.get(resource))

      val taxiSources = TaxiSourcesLoader.loadPackageAndDependencies(
         Paths.get(Resources.getResource("depends-on-sample-project").toURI()),
         PackageImporter(importerConfig(), downloaderFactory)
      )
      taxiSources.sources.size.should.equal(6)

      // Let's compile
      val (compilationErrors,taxi) = Compiler(taxiSources).compileWithMessages()
      compilationErrors.filter { it.severity == Severity.ERROR }.should.be.empty

      // Desk uses an imported type from a dependent repository
      val deskModel = taxi.objectType("withDeps.Desk")
      deskModel.field("traders").type.toQualifiedName().parameterizedName
         .should.equal("lang.taxi.Array<testB.Trader>")
   }


   private fun importerConfig() = ImporterConfig(localCache.root.toPath())
}
