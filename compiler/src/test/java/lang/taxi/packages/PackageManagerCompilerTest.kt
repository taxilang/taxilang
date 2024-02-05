package lang.taxi.packages

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import lang.taxi.Compiler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.taxilang.packagemanager.PackageManager
import org.taxilang.packagemanger.PackageManagerTest
import java.io.File

class PackageManagerCompilerTest {
   @field:TempDir
   lateinit var projctFolder: File

   @field:TempDir
   lateinit var cacheDir: File

   @field:TempDir
   lateinit var remoteRepoDir: File
   @Test
   fun `can compile projects that declare dependencies`() {
      val (packageIdentifier,taxiConfPath) = PackageManagerTest.createTaxiProject(
         projctFolder.toPath(),
         identifier = "org.test/rootProject/0.1.0",
         dependencies = listOf("org.test/dependencyA/0.1.0"),
         typeNames = listOf("Film"),
         taxiSrc = "type NewZealander inherits Person\n\ntype Car inherits Vehicle"
      )
      // DepA - installed in "remote"
      PackageManagerTest.createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyA/0.1.0",
         dependencies = listOf("org.test/dependencyA1/0.1.0"),
         typeNames = listOf("Person")
      )
      // DepA-1 - installed in "remote"
      PackageManagerTest.createTaxiProject(
         remoteRepoDir.toPath(),
         identifier = "org.test/dependencyA1/0.1.0",
         typeNames = listOf("Vehicle")
      )

      val packageManager = PackageManagerTest.buildPackageManager(remoteRepoDir, cacheDir)


      val taxi = Compiler.forPackageWithDependencies(taxiConfPath.parent, packageManager)
         .compile()
      // This is the test - NewZealander inherits from Person, which is another
      // project
      taxi.type("NewZealander")
         .inheritsFrom.shouldHaveSize(1)

      // This type is present from another project
      val personType = taxi.type("NewZealander")
         .inheritsFrom.single()

   }
}
