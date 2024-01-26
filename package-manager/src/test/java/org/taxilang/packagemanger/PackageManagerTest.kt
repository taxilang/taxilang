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
      val (packageIdentifier,baseProjectPath) = createTaxiProject(
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
      val packageManager = buildPackageManager()
      val loaded = packageManager.fetchDependencies(taxiProject)

      // Here's the important bit -- did we load the transitive dependency? (dependencyA1?)
      loaded.shouldHaveSize(2)

      // Also, the dependencies should've been "downloaded" from the remote repositiory, and installed
      // in our local cache:
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/taxi.conf").exists().shouldBeTrue()

      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/taxi.conf").exists().shouldBeTrue()
   }


   @Test
   fun `can install dev project without deps to local repo`() {
      val (packageIdentifier,taxiConfPath) = createTaxiProject(
         projctFolder.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = emptyList(),
         typeNames = listOf("Film")
      )
      val taxiProject = loadProject(taxiConfPath)
      val packageManager = buildPackageManager()
      packageManager.bundleAndInstall(taxiConfPath.parent,taxiProject)

      cacheDir.toPath().resolve("org/test/rootProject/0.1.0/src").exists().shouldBeTrue()
   }

   @Test
   fun `when installing dev project with deps to local repo then deps are installed also`() {
      // Dev project - in the working dir.
      val (packageIdentifier,taxiConfPath) = createTaxiProject(
         projctFolder.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = emptyList(),
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
      val packageManager = buildPackageManager()

      // Test:
      packageManager.bundleAndInstall(taxiConfPath.parent,taxiProject)

      cacheDir.toPath().resolve("org/test/rootProject/0.1.0/src").exists().shouldBeTrue()

      // Also, the dependencies should've been "downloaded" from the remote repositiory, and installed
      // in our local cache:
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/taxi.conf").exists().shouldBeTrue()

      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA1/0.1.0/taxi.conf").exists().shouldBeTrue()
   }



   private fun buildPackageManager(): PackageManager {
      val (repoSystem, session) = RepositorySystemProvider.build(
         listOf(TaxiFileSystemTransportFactory::class.java)
      )
      val remote = RemoteRepository.Builder(
         "test-remote",
         "default", //TaxiProjectLayoutFactory.LAYOUT_TYPE,
         remoteRepoDir.canonicalPath
      ).setPolicy(
         RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_IGNORE
         )
      )

         .build()

      val packageManager = PackageManager(
         ImporterConfig(cacheDir.toPath()),
         repoSystem,
         session,
         listOf(remote)
      )
      return packageManager
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
         typeNames: List<String>,
         createBundle: Boolean = true,
         useNestedFolders: Boolean = true
      ): Pair<PackageIdentifier,Path> {
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
            }
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
   }

}

fun loadProject(path: Path) = TaxiProjectLoader().withConfigFileAt(path).load()
