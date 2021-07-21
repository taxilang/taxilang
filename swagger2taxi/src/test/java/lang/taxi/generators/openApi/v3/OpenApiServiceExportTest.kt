package lang.taxi.generators.openApi.v3

import com.winterbe.expekt.should
import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.testing.TestHelpers.compile
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class OpenApiServiceExportTest {

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
         paths:
           /pets:
             description: Service Desc
             get:
               description: |
                 Returns all pets from the system that the user has access to
                 Nam sed condimentum est. Maecenas tempor sagittis sapien, nec rhoncus sem sagittis sit amet.
               operationId: findPets
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {


            [[ Service Desc ]]

            service PetsService {
               [[ Returns all pets from the system that the user has access to
               Nam sed condimentum est. Maecenas tempor sagittis sapien, nec rhoncus sem sagittis sit amet.
               ]]
               @HttpOperation(method = "GET" , url = "/pets")
               operation findPets(   )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)

      val compiledService = compile(taxiDef.taxi).service("vyne.openApi.PetsService")

      compiledService.typeDoc.should.equal("Service Desc")
      val operation = compiledService.operation("findPets")
      operation.typeDoc.should.equal("""
         Returns all pets from the system that the user has access to
              Nam sed condimentum est. Maecenas tempor sagittis sapien, nec rhoncus sem sagittis sit amet.
         """.trimIndent())
   }

   @Test
   fun `illegal identifiers in service, operation and parameter names are replaced correctly`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         servers:
           - url: http://petstore.swagger.io/api
         paths:
           /pets-pets:
             get:
               operationId: find pets
               parameters:
                 - name: pet limit
                   in: query
                   required: false
                   schema:
                     type: integer
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            service Pets_petsService {
               @HttpOperation(method = "GET" , url = "/pets-pets")
               operation find_pets( pet_limit : Int   )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }
}
