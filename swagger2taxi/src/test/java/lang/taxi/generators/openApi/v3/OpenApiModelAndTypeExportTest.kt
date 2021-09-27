package lang.taxi.generators.openApi.v3

import com.winterbe.expekt.should
import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.testing.TestHelpers.compile
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class OpenApiModelAndTypeExportTest {

   @Test
   fun `taxi documentation is generated from openapi description`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         servers:
           - url: http://petstore.swagger.io/api
         paths: {}
         components:
           schemas:
             Pet:
               description: A Dog or Cat
               properties:
                 id:
                   description: The ID of the Pet
                   type: integer
                   format: int64
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")
      val compiledModel = compile(taxiDef.taxi).model("vyne.openApi.Pet")

      compiledModel.typeDoc.should.equal("A Dog or Cat")
      val field = compiledModel.field("id")
      field.typeDoc.should.equal("The ID of the Pet")
   }

   @Test
   fun `illegal identifiers in model & field names are replaced correctly`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         servers:
           - url: http://petstore.swagger.io/api
         paths: {}
         components:
           schemas:
             Pet:
               properties:
                 pet id:
                   type: integer
                   format: int64
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            model Pet {
               pet_id : Int
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }
}
