package org.taxilang.packagemanager.transports

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import lang.taxi.packages.ImporterConfig
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.taxilang.packagemanager.PackageManager
import org.taxilang.packagemanager.RepositorySystemProvider
import org.taxilang.packagemanager.repository.git.GitRepoTransportFactory
import org.taxilang.packagemanager.repository.git.GitRepositorySupport
import org.taxilang.packagemanger.PackageManagerTest
import org.taxilang.packagemanger.loadProject
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists

class GitRepoTransportTest {

   @field:TempDir
   lateinit var cacheDir: File

   @field:TempDir
   lateinit var tempWorkdir: File

   @Test
   fun `will load dependencies from remote git repo`() {
      // Base Project
      val (packageIdentifier, baseProjectPath) = PackageManagerTest.createTaxiProject(
         tempWorkdir.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         // This is a real test git project, deployed on gitlab
         dependencies = listOf("org.test/dependencyA/https://gitlab.com/taxi-lang/test-project-a.git"),
         typeNames = listOf("Film")
      )
      val taxiProject = loadProject(baseProjectPath)
      val packageManager = buildPackageManager(cacheDir.toPath())
      val loaded = packageManager.fetchDependencies(taxiProject)

      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()

   }

   @Test
   fun `will load dependencies from git`() {
      // DepB - installed in "remote"
      val depBGitUrl = PackageManagerTest.createTaxiProject(
         tempWorkdir.toPath(),
         identifier = "org.test/dependencyB/0.1.0",
         typeNames = listOf("Vehicle"),
         useNestedFolders = false
      ).let { (_, taxiConf) -> initAsGitRepo(taxiConf.parent) }

      // DepA - installed in "remote"
      val depAGitUrl = PackageManagerTest.createTaxiProject(
         tempWorkdir.toPath(),
         identifier = "org.test/dependencyA/0.1.0",
         dependencies = listOf("org.test/dependencyB/${depBGitUrl.toASCIIString()}"),
         typeNames = listOf("Person"),
         useNestedFolders = false
      ).let { (_, taxiConf) -> initAsGitRepo(taxiConf.parent) }


      // Base Project
      val (packageIdentifier, baseProjectPath) = PackageManagerTest.createTaxiProject(
         tempWorkdir.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = listOf("org.test/dependencyA/${depAGitUrl.toASCIIString()}"),
         typeNames = listOf("Film")
      )
      val taxiProject = loadProject(baseProjectPath)
      val packageManager = buildPackageManager(cacheDir.toPath())
      val loaded = packageManager.fetchDependencies(taxiProject)

      // Here's the important bit -- did we load the transitive dependency? (dependencyA1?)
      loaded.shouldHaveSize(2)

      // Also, the dependencies should've been "downloaded" from the remote repositiory, and installed
      // in our local cache:
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyA/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()

      cacheDir.toPath().resolve("org/test/dependencyB/0.1.0/bundle/src").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyB/0.1.0/bundle/src/types.taxi").exists().shouldBeTrue()
      cacheDir.toPath().resolve("org/test/dependencyB/0.1.0/bundle/taxi.conf").exists().shouldBeTrue()
   }

   private fun initAsGitRepo(path: Path): URI {
      val git = Git.init().setDirectory(path.toFile()).call()
      git.add().addFilepattern(".").call()
      git.commit().setMessage("Initial commit").setAll(true).call()
      return path.resolve(".git").toUri()

   }

}

fun buildPackageManager(cacheDir:Path): PackageManager {
   val (repoSystem, session) = RepositorySystemProvider.build()
   val packageManager = PackageManager(
      ImporterConfig(cacheDir),
      repoSystem,
      session,
   )
   return packageManager
}
