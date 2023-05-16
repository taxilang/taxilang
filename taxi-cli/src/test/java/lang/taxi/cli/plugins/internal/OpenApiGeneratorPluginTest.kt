package lang.taxi.cli.plugins.internal

import com.google.common.io.Resources
import com.winterbe.expekt.should
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import kotlin.io.path.readText

class OpenApiGeneratorPluginTest {
   @TempDir
   @JvmField
   var folder: File? = null

   private fun copyProject(path: String) {
      val testProject = File(Resources.getResource(path).toURI())
      FileUtils.copyDirectory(testProject, folder!!)
   }

   @Test
   fun generatesOpenApiSpec() {
      copyProject("samples/open-api")
      executeBuild(folder!!.toPath(), listOf(OpenApiGeneratorPlugin()))

      val expected = Resources.getResource("samples/open-api/expected/PersonService.yaml").readText()
      val expectedOas = Yaml.mapper().readValue(expected, OpenAPI::class.java)

      val actual = folder!!.toPath().resolve("dist/open-api/PersonService.yaml").readText()
      val actualOas = Yaml.mapper().readValue(actual, OpenAPI::class.java)

      actualOas.should.equal(expectedOas)
   }

   @Test
   fun `when no services match the provided filter then nothing is output`() {
      copyProject("samples/open-api-no-match-filter")
      executeBuild(folder!!.toPath(), listOf(OpenApiGeneratorPlugin()))

      val output = folder!!.toPath().resolve("dist")
      Files.exists(output).should.be.`false`
   }


   @Test
   fun `only generates for filtered services`() {
      copyProject("samples/open-api-filtered")
      executeBuild(folder!!.toPath(), listOf(OpenApiGeneratorPlugin()))

      val expected = Resources.getResource("samples/open-api-filtered/expected/PersonService.yaml").readText()
      val expectedOas = Yaml.mapper().readValue(expected, OpenAPI::class.java)

      val actual = folder!!.toPath().resolve("dist/open-api/PersonService.yaml").readText()
      val actualOas = Yaml.mapper().readValue(actual, OpenAPI::class.java)

      actualOas.should.equal(expectedOas)
   }


}
