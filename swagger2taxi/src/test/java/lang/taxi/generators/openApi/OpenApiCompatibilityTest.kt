package lang.taxi.generators.openApi

import com.winterbe.expekt.expect
import lang.taxi.generators.*
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import lang.taxi.utils.log
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.fail
import kotlin.text.Charsets.UTF_8

class OpenApiCompatibilityTest {
    val sources = listOf(
            testFile("/openApiSpec/v3.0/api-with-examples.yaml"),
            testFile("/openApiSpec/v3.0/callback-example.yaml"),
            testFile("/openApiSpec/v3.0/petstore.yaml"),
            testFile("/openApiSpec/v3.0/petstore-expanded.yaml"),
            testFile("/openApiSpec/v3.0/uspto.yaml"),
       // Need to address "No URL provided in the set of servers" error to get this importing
//            testFile("/openApiSpec/v3.0/jira-swagger-v3.json")
    )

   @Test
   fun canImportJiraSwagger() {
      // This swagger is interesting because it's big and complex.
      // Also, it's a consumer product, so we had to introduce the ability to generate from swagger
      // without knowing the end url, and supplying that as an extra param.
      val (_,jira) = testFile("/openApiSpec/v3.0/jira-swagger-v3.json")
      val generator = TaxiGenerator()
      val taxi = generator.generateAsStrings(jira, "vyne.openApi", GeneratorOptions(serviceBasePath = "http://myjira/"))
      expect(taxi.successful).to.be.`true`
      expect(taxi.messages.hasErrors()).to.be.`false`
      val expectedOutputFile = "/openApiSpec/v3.0/jira-swagger-v3.json.taxi"
      expectToCompileTheSameReplacingResult(taxi, expectedOutputFile)
   }

   @Test
   fun detectsCorrectVersion() {
      val generator = TaxiGenerator();
      expect(generator.detectVersion(testFile("/openApiSpec/v3.0/jira-swagger-v3.json").second)).to.equal(TaxiGenerator.SwaggerVersion.OPEN_API)
      expect(generator.detectVersion(testFile("/openApiSpec/v3.0/uspto.yaml").second)).to.equal(TaxiGenerator.SwaggerVersion.OPEN_API)
      expect(generator.detectVersion(testFile("/openApiSpec/v2.0/yaml/petstore-expanded.yaml").second)).to.equal(TaxiGenerator.SwaggerVersion.SWAGGER_2)
      expect(generator.detectVersion(testFile("/openApiSpec/v2.0/json/pets.json").second)).to.equal(TaxiGenerator.SwaggerVersion.SWAGGER_2)
   }

    @TestFactory
    fun canImportAllSwaggerFiles(): List<DynamicTest> {
        val generator = TaxiGenerator();
        return sources.map { (filename, source) ->
           dynamicTest("Can import swagger file $filename") {
              val taxiDef = generator.generateAsStrings(source, "vyne.openApi")
              val issues = (if (taxiDef.taxi.isEmpty()) {
                 listOf(
                    Message(
                       Level.ERROR,
                       "No source generated"
                    )
                 )
              } else emptyList()) + taxiDef.messages
              if (issues.hasErrors()) {
                 issues.filter { it.level == Level.ERROR }.forEach { log().error("==> ${it.message}") }
              }
              if (issues.hasWarnings()) {
                 issues.filter { it.level == Level.WARN }.forEach { message -> log().warn("==> $message") }
              }
              if (issues.hasErrors()) {
                 fail("Some schemas failed to import")
              }

              // Check the result can compile
              expectToCompileTheSameReplacingResult(taxiDef, "$filename.taxi")
           }
        }
    }

   private fun expectToCompileTheSameReplacingResult(
      taxi: GeneratedTaxiCode,
      expectedOutputFile: String
   ) {
      val expected = testResource(expectedOutputFile)
      storeTheResult(expectedOutputFile, taxi)
      expectToCompileTheSame(taxi.taxi, expected)
   }

   // Convenient way to generate the actual taxi expectation
   // Can then check if it matches what you wanted, and check it in - giving a
   // history of how the generated taxi output has changed over time
   private fun storeTheResult(
      expectedOutputFile: String,
      taxi: GeneratedTaxiCode
   ) {
      val runningOnCi = System.getenv("CI")?.toBoolean() ?: false
      if (!runningOnCi) {
         File("src/test/resources$expectedOutputFile")
            .writeText(taxi.taxi.single(), UTF_8)
      }
   }
}
