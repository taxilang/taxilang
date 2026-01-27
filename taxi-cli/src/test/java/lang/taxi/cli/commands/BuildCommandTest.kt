package lang.taxi.cli.commands

import com.google.common.io.Resources
import lang.taxi.cli.plugins.internal.executeBuild
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BuildCommandTest {

   @TempDir
   lateinit var folder: File

   @Test
   fun `can build a project referencing stdlib types`() {
      folder.deployProject("samples/references-std-lib")
      executeBuild(folder.toPath(), emptyList())
   }

}


fun File.deployProject(path: String) {
   val testProject = File(Resources.getResource(path).toURI())
   FileUtils.copyDirectory(testProject, this)
}
