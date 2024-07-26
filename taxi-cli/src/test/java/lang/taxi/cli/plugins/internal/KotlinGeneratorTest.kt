package lang.taxi.cli.plugins.internal

import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.info.BuildProperties
import java.io.File
import java.util.*

class KotlinGeneratorTest {

   @TempDir
   lateinit var folder: File

   @Test
   fun `does not generate stdlib classes`() {
      folder.deployProject("samples/maven")
      executeBuild(folder.toPath(), listOf(KotlinPlugin(BuildProperties(Properties()))))

      folder.resolve("dist/src/main/java/taxi/http").shouldNotExist()
      folder.resolve("dist/src/main/java/taxi/stdlib").shouldNotExist()
      folder.resolve("dist/src/main/java/com/foo").shouldExist()

   }
}
