package lang.taxi.packages.repository

import com.google.common.io.Resources
import com.winterbe.expekt.should
import lang.taxi.packages.Credentials
import lang.taxi.packages.PackageDownloader
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.PackagePublisher
import lang.taxi.packages.ProjectName
import lang.taxi.packages.Repository
import lang.taxi.packages.utils.basicAuth
import lang.taxi.packages.utils.log
import org.apache.commons.io.IOUtils
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.Assert
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

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
// @Disabled
@Testcontainers
class NexusPackageServiceTest {
   @TempDir
   @JvmField
   var folder: Path? = null

   private lateinit var adminPassword: String

   companion object {
      @JvmField
      @TempDir
      var nexusDataDirectory: Path? = null

      @Container
      @JvmField
      val nexusContainer: NexusContainer =
         NexusContainer()
            .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger(NexusContainer::class.java)))
//            .withNexusDataDirectory(nexusDataDirectory)
            .withExposedPorts(8081)
            .waitingFor(
               Wait.forHttp("/service/rest/v1/status")
                  .withStartupTimeout(
                     // Docs say can take up to 3 minutes
                     Duration.ofMinutes(5)
                  )
            )
   }


   @BeforeEach
   fun discoverAdminUser() {
      nexusContainer.copyFileFromContainer("/nexus-data/admin.password") { stream ->
         adminPassword = IOUtils.toString(stream, Charset.defaultCharset())
         log().info("Fetched nexus admin password from container")
      }

   }

   //   @BeforeEach
   fun assumeNexusIsRunning() {
      val client = ApacheClient()
      try {
         val response = client(Request(Method.GET, "http://localhost:8081/service/rest/v1/status"))
         assumeTrue(response.status.code == 200, "Not running nexus tests, as nexus does not appear to be up")
      } catch (e: Exception) {
         log().error(
            "Error caught whilst trying to perform nexus liveliness checks, will not run Nexus tests",
            e.message
         )
         Assert.assertTrue(false)
         assumeTrue(false)
      }

   }


   // Requires that nexus is running
   @Test
//   @Disabled
   fun canPublishPackage() {
      val nexus = configureNexusWithEmptyRepository("canPublishPackage")
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0").toURI()
      PackagePublisher(SingleServiceFactory(nexus)).publish(Paths.get(resource))
   }

   private fun configureNexusWithEmptyRepository(repositoryName: String): NexusPackageService {
      val localPort = nexusContainer.getMappedPort(8081)

      val client = ApacheClient()
      val request = Request(Method.POST, "http://localhost:$localPort/service/rest/v1/repositories/raw/hosted")
         .header("Content-Type", "application/json")
         .basicAuth("admin", adminPassword)
         .body(
            """{
  "name": "$repositoryName",
  "online": true,
  "storage": {
    "blobStoreName": "default",
    "strictContentTypeValidation": true,
    "writePolicy": "allow_once"
  }
}"""
         )
      val response = client(request)
      response.status.code.should.equal(201)
      log().info("Successfully created nexus repository $repositoryName")
      return NexusPackageService(
         nexusUrl = "http://localhost:$localPort",
         repositoryName = repositoryName,
         credentials = Credentials("nexus", "admin", adminPassword)
      )

   }

   @Test
   fun canPublishAndDownloadPackage() {
      val nexus = configureNexusWithEmptyRepository("canPublishAndDownloadPackage")
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0").toURI()
      PackagePublisher(SingleServiceFactory(nexus)).publish(Paths.get(resource))
      val nexusRepository = Repository(
         nexus.nexusUrl, "Local nexus", type = "nexus",
         settings = mapOf(
            NexusPackageService.Companion.Settings.REPOSITORY_NAME to nexus.repositoryName
         )
      )
      val downloader = PackageDownloader(
         downloadPath = folder!!,
         repositories = listOf(nexusRepository),
         credentials = emptyList()
      )
      downloader.download(PackageIdentifier(ProjectName.fromId("taxi/Demo"), "0.2.0"))
      val downloadPath = folder!!
      Files.exists(downloadPath.resolve("taxi/Demo/0.2.0/taxi.conf")).should.be.`true`
      Files.exists(downloadPath.resolve("taxi/Demo/0.2.0/financial-terms.taxi")).should.be.`true`
      Files.exists(downloadPath.resolve("taxi/Demo/0.2.0/Sample.taxi")).should.be.`true`
   }
}

class NexusContainer : GenericContainer<NexusContainer>(DockerImageName.parse("sonatype/nexus3")) {
   fun withNexusDataDirectory(temporaryFolder: Path): NexusContainer {

      log().info("Using nexus temp directory at ${temporaryFolder.toFile().canonicalPath}")
      val user200 = temporaryFolder.fileSystem.userPrincipalLookupService.lookupPrincipalByName("200");
      Files.setOwner(temporaryFolder, user200)
      return withNexusDataDirectory(temporaryFolder)
   }

   fun withNexusDataDirectory(file: File): NexusContainer {
      addFileSystemBind(file.canonicalPath, "/nexus-data", BindMode.READ_WRITE)
      return this
   }
}
