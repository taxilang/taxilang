package org.taxilang.packagemanger

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import lang.taxi.packages.ImporterConfig
import lang.taxi.packages.PackageIdentifier
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.writers.ConfigWriter
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.taxilang.packagemanager.PackageManager
import org.taxilang.packagemanager.RepositorySystemProvider
import org.taxilang.packagemanager.TaxiFileBasedPackageBundler
import org.taxilang.packagemanager.TaxiProjectLoader
import org.taxilang.packagemanager.transports.TaxiFileSystemTransportFactory
import org.taxilang.packagemanager.transports.TaxiFileSystemUtils
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class PackageManagerTest {
   @field:TempDir
   lateinit var projctFolder: File

   @field:TempDir
   lateinit var cacheDir: File

   @field:TempDir
   lateinit var remoteRepoDir: File


   @Test
   fun `can resolve transitive dependencies from file repo`() {
      // Base Project
      val (packageIdentifier, baseProjectPath) = createTaxiProject(
         projctFolder.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = listOf("org.test/dependencyA/0.1.0"),
         typeNames = listOf("Film")
      )
      // DepA - installed in "remote"
      Companion.createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyA/0.1.0",
         dependencies = listOf("org.test/dependencyA1/0.1.0"),
         typeNames = listOf("Person")
      )
      // DepA-1 - installed in "remote"
      Companion.createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyA1/0.1.0",
         typeNames = listOf("Vehicle")
      )

      val taxiProject = loadProject(baseProjectPath)
      val packageManager = buildPackageManager(remoteRepoDir, cacheDir)
      val loaded = packageManager.fetchDependencies(taxiProject)

      // Here's the important bit -- did we load the transitive dependency? (dependencyA1?)
      loaded.shouldHaveSize(2)

      // Also, the dependencies should've been "downloaded" from the remote repositiory, and installed
      // in our local cache:
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()

      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()
   }


   @Test
   fun `can install dev project without deps to local repo`() {
      val (packageIdentifier, taxiConfPath) = createTaxiProject(
         projctFolder.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = emptyList(),
         typeNames = listOf("Film")
      )
      val taxiProject = loadProject(taxiConfPath)
      val packageManager = buildPackageManager(remoteRepoDir, cacheDir)
      packageManager.bundleAndInstall(taxiConfPath.parent, taxiProject)

      cacheDir.toPath().resolve("org/test/rootProject/0.1.0/bundle/src").exists().shouldBeTrue()
   }

   @Test
   fun `when installing dev project with deps to local repo then deps are installed also`() {
      // Dev project - in the working dir.
      val (packageIdentifier, taxiConfPath) = createTaxiProject(
         projctFolder.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = listOf("org.test/dependencyA/0.1.0"),
         typeNames = listOf("Film")
      )
      // DepA - installed in "remote"
      createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyA/0.1.0",
         dependencies = listOf("org.test/dependencyA1/0.1.0"),
         typeNames = listOf("Person")
      )
      // DepA-1 - installed in "remote"
      createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyA1/0.1.0",
         typeNames = listOf("Vehicle")
      )


      val taxiProject = loadProject(taxiConfPath)
      val packageManager = buildPackageManager(remoteRepoDir, cacheDir)

      // Test:
      packageManager.bundleAndInstall(taxiConfPath.parent, taxiProject)

      cacheDir.toPath().resolve("org/test/rootProject/0.1.0/bundle/src").exists().shouldBeTrue()

      // Also, the dependencies should've been "downloaded" from the remote repositiory, and installed
      // in our local cache:
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()

      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()
   }

   @Test
   fun `will resolve from local cache if present`() {
      val (packageIdentifier, taxiConfPath) = createTaxiProject(
         projctFolder.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = listOf("org.test/dependencyA/0.1.0"),
      )
      // DepA - installed in "remote"
      createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyA/0.1.0",
         dependencies = listOf("org.test/dependencyB/0.1.0"),
      )
      // DepB - installed in "remote"
      createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyB/0.1.0",
      )

      val taxiProject = loadProject(taxiConfPath)
      // first, use the package manager to "install" our required dependencies in our local cache
      val packageManager1 = buildPackageManager(remoteRepoDir, cacheDir, registerRemoteRepository = true)
      packageManager1.bundleAndInstall(taxiConfPath.parent, taxiProject)

      // Now create a seperate packageManager without the remote, but with the same local cache.
      // Try and resolve.
      val packageManager2 = buildPackageManager(remoteRepoDir, cacheDir, registerRemoteRepository = false)
      val result = packageManager2.fetchDependencies(taxiProject)
      result.shouldHaveSize(2)

   }

   companion object {
      /**
       * Creates an entire taxi project, with dependencies on other projects.
       * Returns the path of the taxi.conf file, and the package identifier
       */
      fun createTaxiProject(
         baseRepoDirectory: Path,
         identifier: String,
         dependencies: List<String> = emptyList(),
         typeNames: List<String> = emptyList(),
         createBundle: Boolean = true,
         useNestedFolders: Boolean = true,
         taxiSrc: String = ""
      ): Pair<PackageIdentifier, Path> {
         val packageIdentifier = PackageIdentifier.fromId(identifier)
         val projectHome = if (useNestedFolders) {
            TaxiFileSystemUtils.projectPath(baseRepoDirectory, packageIdentifier)
         } else {
            baseRepoDirectory.resolve(packageIdentifier.fileSafeIdentifier)
               .apply { toFile().mkdirs() }
         }
         val taxiConf = TaxiPackageProject(
            name = packageIdentifier.name.id,
            version = packageIdentifier.version.toString(),
            sourceRoot = "src/",
            dependencies = dependencies.associate { dependencyId ->
               val dependencyIdentifier = PackageIdentifier.withoutVersion(dependencyId)
               val version = PackageIdentifier.versionIsh(dependencyId)
               dependencyIdentifier to version
            }
         )
         val config = ConfigWriter().serialize(taxiConf)
         val taxiConfPath = projectHome.resolve("taxi.conf")
         taxiConfPath.writeText(config)

         if (typeNames.isNotEmpty()) {
            val types = typeNames.joinToString("\n") {
               "type $it inherits String"
            } + "\n$taxiSrc"
            projectHome.resolve("src/types.taxi").apply {
               this.toFile().parentFile.mkdirs()
               this.writeText(types)
            }
         }
         println("Created taxi project $identifier at $projectHome")

         if (createBundle) {
            val bundle = TaxiFileBasedPackageBundler.createBundle(projectHome, packageIdentifier)
            bundle.copyTo(projectHome)
         }
         return packageIdentifier to taxiConfPath
      }

      fun buildPackageManager(
         remoteRepoDir: File,
         cacheDir: File,
         registerRemoteRepository: Boolean = true
      ): PackageManager {
         val (repoSystem, session) = RepositorySystemProvider.build(
            listOf(TaxiFileSystemTransportFactory::class.java)
         )
         val remoteRepositories = if (registerRemoteRepository) {
            listOf(
               RemoteRepository.Builder(
                  "test-remote",
                  "default", //TaxiProjectLayoutFactory.LAYOUT_TYPE,
                  remoteRepoDir.canonicalPath
               ).setPolicy(
                  RepositoryPolicy(
                     true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_IGNORE
                  )
               ).build()
            )
         } else emptyList()

         val packageManager = PackageManager(
            ImporterConfig(cacheDir.toPath()),
            repoSystem,
            session,
            remoteRepositories
         )
         return packageManager
      }

   }

}

fun loadProject(path: Path) = TaxiProjectLoader(path).load()
