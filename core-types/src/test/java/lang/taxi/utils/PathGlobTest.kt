package lang.taxi.utils

import com.google.common.io.Resources
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.mpp.file
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PathGlobTest {

   @TempDir
   @JvmField
   var folder: File? = null

   @Test
   fun normalizeGlobPattern() {
      PathGlob.expandGlobWithBaseDirectory("**/*.taxi").shouldBe("{*.taxi,**/*.taxi}")
      PathGlob.expandGlobWithBaseDirectory("src/**/*.taxi").shouldBe("{src/*.taxi,src/**/*.taxi}")
      PathGlob.expandGlobWithBaseDirectory("src/*.taxi").shouldBe("src/*.taxi")
      PathGlob.expandGlobWithBaseDirectory("**/*{.txt,.md}").shouldBe("{*.txt,**/*.txt,*.md,**/*.md}")
      PathGlob.expandGlobWithBaseDirectory("src/**/*{.wsdl,.xsd}").shouldBe("{src/*.wsdl,src/**/*.wsdl,src/*.xsd,src/**/*.xsd}")
   }

   @Test
   fun `glob returns correct matches when starting with double-star`() {
      folder!!.deployProject("glob-test")
      val glob = PathGlob(folder!!.toPath(), "**/*.txt")
      val files = glob.mapEachDirectoryEntry { folder!!.toPath().relativize(it).toString() }
      files.values.shouldContainAll("a.txt", "nest-1/a1.txt","nest-1/nest-2/a2.txt" )
   }

   @Test
   fun `glob returns correct matches when double-star in middle with double-star`() {
      folder!!.deployProject("glob-test")
      val glob = PathGlob(folder!!.toPath(), "nest-1/**/*.txt")
      val files = glob.mapEachDirectoryEntry { folder!!.toPath().relativize(it).toString() }
      files.values.shouldContainAll("nest-1/a1.txt","nest-1/nest-2/a2.txt" )
   }
   @Test
   fun `glob returns correct matches when no double-star`() {
      folder!!.deployProject("glob-test")
      val glob = PathGlob(folder!!.toPath(), "nest-1/*.txt")
      val files = glob.mapEachDirectoryEntry { folder!!.toPath().relativize(it).toString() }
      files.values.shouldContainAll("nest-1/a1.txt" )
   }
   @Test
   fun `glob returns correct matches when no double-star and using multiple extensions`() {
      folder!!.deployProject("glob-test")
      val glob = PathGlob(folder!!.toPath(), "nest-1/*{.txt,.md}")
      val files = glob.mapEachDirectoryEntry { folder!!.toPath().relativize(it).toString() }
      files.values.shouldContainAll("nest-1/a1.txt" , "nest-1/a1.md")
   }

   @Test
   fun `glob returns correct matches when starts with double-star and using multiple extensions`() {
      folder!!.deployProject("glob-test")
      val glob = PathGlob(folder!!.toPath(), "**/*{.txt,.md}")
      val files = glob.mapEachDirectoryEntry { folder!!.toPath().relativize(it).toString() }
      files.values.shouldContainAll("a.txt", "b.txt", "nest-1/a1.txt" , "nest-1/a1.md", "nest-1/nest-2/a2.txt" , "nest-1/nest-2/a2.md")
   }
   @Test
   fun `glob returns correct matches when contains with double-star in middle and using multiple extensions`() {
      folder!!.deployProject("glob-test")
      val glob = PathGlob(folder!!.toPath(), "nest-1/**/*{.txt,.md}")
      val files = glob.mapEachDirectoryEntry { folder!!.toPath().relativize(it).toString() }
      files.values.shouldContainAll("nest-1/a1.txt" , "nest-1/a1.md", "nest-1/nest-2/a2.txt" , "nest-1/nest-2/a2.md")
   }
}


fun File.deployProject(path: String) {
   val testProject = File(Resources.getResource(path).toURI())
   FileUtils.copyDirectory(testProject, this)
}
