package lang.taxi.generators.openApi

import com.winterbe.expekt.expect
import lang.taxi.generators.Level
import lang.taxi.generators.Message
import lang.taxi.generators.hasErrors
import lang.taxi.generators.hasWarnings
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import lang.taxi.utils.log
import org.junit.Test
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.fail

class OpenApiCompatabilityTest {
    val sources = listOf(
            testFile("/openApiSpec/v2.0/json/api-with-examples.json"),
            testFile("/openApiSpec/v2.0/json/pets.json"),
            testFile("/openApiSpec/v2.0/json/petstore.json"),
            testFile("/openApiSpec/v2.0/json/petstore-expanded.json"),
            testFile("/openApiSpec/v2.0/json/petstore-minimal.json"),
            testFile("/openApiSpec/v2.0/json/petstore-simple.json"),
            testFile("/openApiSpec/v2.0/json/petstore-with-external-docs.json"),
            testFile("/openApiSpec/v2.0/json/uber.json"),
            testFile("/openApiSpec/v2.0/yaml/api-with-examples.yaml"),
            testFile("/openApiSpec/v2.0/yaml/petstore.yaml"),
            testFile("/openApiSpec/v2.0/yaml/petstore-expanded.yaml"),
            testFile("/openApiSpec/v2.0/yaml/petstore-minimal.yaml"),
            testFile("/openApiSpec/v2.0/yaml/petstore-simple.yaml"),
            testFile("/openApiSpec/v2.0/yaml/petstore-with-external-docs.yaml"),
            testFile("/openApiSpec/v2.0/yaml/uber.yaml"),
            testFile("/openApiSpec/v3.0/api-with-examples.yaml"),
            testFile("/openApiSpec/v3.0/callback-example.yaml"),
            testFile("/openApiSpec/v3.0/petstore.yaml"),
            testFile("/openApiSpec/v3.0/petstore-expanded.yaml"),
            testFile("/openApiSpec/v3.0/uspto.yaml")
       // Need to address "No URL provided in the set of servers" error to get this importing
//            testFile("/openApiSpec/v3.0/jira-swagger-v3.json")
    )

   @Test
   fun canImportJiraSwagger() {
      // This swagger is interesting because it's big and complex.
      // Also, it's a consumer proudct, so we had to introduce the ability to generate from swagger
      // wihtout knowing the end url, and supplying that as an extra param.
      val (_,jira) = testFile("/openApiSpec/v3.0/jira-swagger-v3.json")
      val generator = TaxiGenerator()
      val taxi = generator.generateAsStrings(jira, "vyne.openApi", GeneratorOptions(serviceBasePath = "http://myjira/"))
      expect(taxi.successful).to.be.`true`
      expect(taxi.messages.hasErrors()).to.be.`false`
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
              expectToCompileTheSame(taxiDef.taxi, testResource("$filename.taxi"))
           }
        }
    }
}
