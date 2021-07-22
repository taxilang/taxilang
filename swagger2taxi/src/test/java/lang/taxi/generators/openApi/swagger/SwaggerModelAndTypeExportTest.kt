package lang.taxi.generators.openApi.swagger

import com.winterbe.expekt.should
import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.testing.TestHelpers.compile
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class SwaggerModelAndTypeExportTest {

   @Test
   fun `taxi documentation is generated from openapi description`() {
      @Language("yaml")
      val openApiSpec = """
         swagger: "2.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         host: petstore.swagger.io
         basePath: /v1
         paths: {}
         definitions:
           Pet:
             description: A Dog or Cat
             properties:
               id:
                 description: The ID of the Pet
                 type: integer
                 format: int64
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            model Pet {
               id : Int?
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")
      taxiDef.taxi.forEach(::println)

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)

      val compiledModel = compile(taxiDef.taxi).model("vyne.openApi.Pet")

      compiledModel.typeDoc.should.equal("A Dog or Cat")
      val field = compiledModel.field("id")
      field.typeDoc.should.equal("The ID of the Pet")
   }
}
