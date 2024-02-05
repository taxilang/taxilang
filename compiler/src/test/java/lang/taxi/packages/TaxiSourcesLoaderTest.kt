package lang.taxi.packages

import com.google.common.io.Resources
import com.winterbe.expekt.should
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import lang.taxi.Compiler
import lang.taxi.messages.Severity
import lang.taxi.packages.SimpleFilePackageService.Companion.fileServiceFactory
import lang.taxi.packages.repository.PackageServiceFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.toPath

class TaxiSourcesLoaderTest {

   @TempDir
   @JvmField
   var localCache: Path? = null

   @TempDir
   @JvmField
   var mockRepoRoot: Path? = null
   private lateinit var packageService: SimpleFilePackageService
   private lateinit var packageServiceFactory: PackageServiceFactory
   private lateinit var downloaderFactory: PackageDownloaderFactory

   @BeforeEach
   fun setup() {
      val (packageService, packageServiceFactory) = fileServiceFactory(mockRepoRoot!!)
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

   @Test
   fun `loads and makes absolute additional sources`() {
      val resource = Resources.getResource("otherSources").toURI()
      val taxiProject = TaxiSourcesLoader.loadPackage(resource.toPath())
      taxiProject.project.additionalSources.shouldNotBeEmpty()
   }

   @Test
   fun `rejects absolute source paths that are not relative to root`() {
      val resource = Resources.getResource("otherSourcesIllegal").toURI()
      val taxiProject = TaxiSourcesLoader.loadPackage(resource.toPath())
      // TODO ... not sure how to test this....sorry future me.
   }


   private fun importerConfig() = ImporterConfig(localCache!!)
}
