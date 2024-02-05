package org.taxilang.packagemanager.repository.nexus

import com.winterbe.expekt.should
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import lang.taxi.packages.Credentials
import lang.taxi.packages.Repository
import org.apache.commons.io.IOUtils
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.taxilang.packagemanager.transports.buildPackageManager
import org.taxilang.packagemanager.utils.basicAuth
import org.taxilang.packagemanager.utils.log
import org.taxilang.packagemanger.PackageManagerTest
import org.taxilang.packagemanger.PackageManagerTest.Companion.createTaxiProject
import org.taxilang.packagemanger.loadProject
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
import java.time.Duration
import java.time.LocalTime
import kotlin.io.path.exists
import kotlin.math.truncate

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
@Testcontainers
class NexusPackageServiceTest {
   @field:TempDir
   lateinit var cacheDir: File

   @field:TempDir
   lateinit var projectFolder: File

   @field:TempDir
   lateinit var tempWorkdir: File


   private lateinit var adminPassword: String

   companion object {
      @JvmField
      @TempDir
      var nexusDataDirectory: Path? = null

      @Container
      @JvmField
      val nexusContainer: NexusContainer =
         NexusContainer()
            // DON'T COMMIT THIS LINE!
//            .withReuse(true)
//            .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger(NexusContainer::class.java)))
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



   private fun configureNexusWithEmptyRepository(repositoryName: String): NexusPackageService {
      val localPort = nexusContainer.getMappedPort(8081)

      val client = ApacheClient()
      val request = Request(Method.POST, "http://${nexusContainer.host}:$localPort/service/rest/v1/repositories/raw/hosted")
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
         nexusUrl = "http://${nexusContainer.host}:$localPort",
         repositoryName = repositoryName,
         credentials = Credentials("nexus", "admin", adminPassword)
      )

   }

   @Test
   fun canPublishAndDownloadPackage() {

      val repositoryName = "canPublishPackage-${LocalTime.now().toSecondOfDay()}"
      log().info("Using nexus repo $repositoryName")
      val nexus = configureNexusWithEmptyRepository(repositoryName)
      // Declare our running nexus as a repo, and create a project that depends on our published artifact
      val nexusRepository = Repository(
         nexus.nexusUrl, repositoryName, type = "nexus",
         settings = mapOf(
            NexusPackageService.REPOSITORY_NAME_PARAM_NAME to nexus.repositoryName
         )
      )

      // Project we'll depend on
      val (_, dependencyTaxiConfPath) = createTaxiProject(
         tempWorkdir.toPath(),
         identifier = "org.test/dependencyA/0.1.0",
         typeNames = listOf("Film"),
         publishTo = nexusRepository
      )
      // publish the project
      PackagePublisher(SingleServiceFactory(nexus)).publish(dependencyTaxiConfPath.parent)


      // Project with dependency on other project
      val (packageIdentifier, baseProjectPath) = createTaxiProject(
         tempWorkdir.toPath(),
         identifier = "org.test/baseProject/0.1.0",
         typeNames = listOf("Person"),
         dependencies = listOf("org.test/dependencyA/0.1.0"),
         repositories = listOf(nexusRepository)
      )

      val taxiProject = loadProject(baseProjectPath)
      val packageManager = buildPackageManager(cacheDir.toPath())
      val resolvedProjects = packageManager.fetchDependencies(taxiProject)

      resolvedProjects.shouldHaveSize(1)
      resolvedProjects.map { it.identifier.id }
         .shouldContain("org.test/dependencyA/0.1.0")
      // Also, the dependencies should've been "downloaded" from the remote repositiory, and installed
      // in our local cache:
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()
   }
}

class NexusContainer : GenericContainer<NexusContainer>(DockerImageName.parse("sonatype/nexus3"))
