package lang.taxi.packages.repository

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.packages.Credentials
import lang.taxi.packages.PackageDownloader
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.PackagePublisher
import lang.taxi.packages.ProjectName
import lang.taxi.packages.Repository
import lang.taxi.packages.utils.log
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * This test explores publishing to nexus.
 * It requires nexus is actually running - will look to find a way to use
 * test containers or something for this later.
 * To get nexus running locally, set up nexus as described here:
 * https://hub.docker.com/r/sonatype/nexus3
 *  - mkdir /some/dir/nexus-data && chown -R 200 /some/dir/nexus-data
 *  - docker run -d -p 8081:8081 --name nexus -v /some/dir/nexus-data:/nexus-data sonatype/nexus3
 *  - Log in as admin, and change the password to "password"
 *  - Create a repository of type RAW, called taxi
 */
// Requires that nexus is running
// @Ignore
class NexusPackageServiceTest {
   @Rule
   @JvmField
   val folder = TemporaryFolder()

   lateinit var adminPassword: String
//
//   companion object {
//      @JvmField
//      @ClassRule
//      val nexusDataDirectory = TemporaryFolder()
//
//      @ClassRule
//      @JvmField
//      val nexusContainer: NexusContainer =
//         NexusContainer()
//            .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger(NexusContainer::class.java)))
//            .withNexusDataDirectory(nexusDataDirectory)
//            .withExposedPorts(9999)
//            .waitingFor(
//               Wait.forHttp("/service/rest/v1/status")
//                  .withStartupTimeout(
//                     // Docs say can take up to 3 minutes
//                     Duration.ofMinutes(5)
//                  )
//            )
//   }

//
//   @Before
//   fun discoverAdminUser() {
//      adminPassword = nexusDataDirectory.root.toPath().resolve("admin.password")
//         .toFile().readText()
//
//   }

   @Before
   fun assumeNexusIsRunning() {
      val client = HttpClientBuilder.create().build()
      try {
         val response = client.execute(HttpGet("http://localhost:8081/service/rest/v1/status"))
         assumeTrue("Not running nexus tests, as nexus does not appear to be up", response.statusLine.statusCode == 200)
      } catch (e: Exception) {
         log().error(
            "Error caught whilst trying to perform nexus liveliness checks, will not run Nexus tests",
            e.message
         )
         assumeTrue(false)
      }

   }

   private val nexus = NexusPackageService(
      nexusUrl = "http://localhost:8081",
      repositoryName = "taxi",
      credentials = Credentials("nexus", "admin", "password")
   )

   // Requires that nexus is running
   @Test
//   @Ignore
   fun canPublishPackage() {
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0").toURI()
      PackagePublisher(SingleServiceFactory(nexus)).publish(Paths.get(resource))
   }

   @Test
   fun canDownloadPackage() {
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0").toURI()
      PackagePublisher(SingleServiceFactory(nexus)).publish(Paths.get(resource))
      val nexusRepository = Repository(
         "http://localhost:8081", "Local nexus", type = "nexus",
         settings = mapOf(
            NexusPackageService.Companion.Settings.REPOSITORY_NAME to "taxi"
         )
      )
      val downloader = PackageDownloader(
         downloadPath = folder.root.toPath(),
         repositories = listOf(nexusRepository),
         credentials = emptyList()
      )
      downloader.download(PackageIdentifier(ProjectName.fromId("taxi/Demo"), "0.2.0"))
      val downloadPath = folder.root.toPath()
      Files.exists(downloadPath.resolve("taxi/Demo/0.2.0/taxi.conf")).should.be.`true`
      Files.exists(downloadPath.resolve("taxi/Demo/0.2.0/financial-terms.taxi")).should.be.`true`
      Files.exists(downloadPath.resolve("taxi/Demo/0.2.0/Sample.taxi")).should.be.`true`
   }
}

class NexusContainer : GenericContainer<NexusContainer>(DockerImageName.parse("sonatype/nexus3")) {
   fun withNexusDataDirectory(temporaryFolder: TemporaryFolder): NexusContainer {
      temporaryFolder.create()

      log().info("Using nexus temp directory at ${temporaryFolder.root.canonicalPath}")
      val tempRoot = temporaryFolder.root
      val user200 = tempRoot.toPath().fileSystem.userPrincipalLookupService.lookupPrincipalByName("200");
      Files.setOwner(tempRoot.toPath(), user200)
      return withNexusDataDirectory(tempRoot)
   }

   fun withNexusDataDirectory(file: File): NexusContainer {
      addFileSystemBind(file.canonicalPath, "/nexus-data", BindMode.READ_WRITE)
      return this
   }
}
