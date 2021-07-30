package lang.taxi.generators.openApi.swagger

import com.winterbe.expekt.should
import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.testing.TestHelpers.compile
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class SwaggerServiceExportTest {

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
         paths:
           /pets:
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

            service PetsService {
               [[ Returns all pets from the system that the user has access to
               Nam sed condimentum est. Maecenas tempor sagittis sapien, nec rhoncus sem sagittis sit amet.
               ]]
               @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/v1/pets")
               operation findPets(   )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)

      val compiledService = compile(taxiDef.taxi).service("vyne.openApi.PetsService")

      compiledService.typeDoc.should.be.`null`
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
         swagger: "2.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         host: petstore.swagger.io
         basePath: /v1
         paths:
           /pets-pets:
             get:
               operationId: find pets
               parameters:
                 - name: pet limit
                   in: query
                   type: integer
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            service Pets_petsService {
               @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/v1/pets-pets")
               operation find_pets( pet_limit : Int   )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `path parameters are annotated correctly`() {
      @Language("yaml")
      val openApiSpec = """
         swagger: "2.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         host: petstore.swagger.io
         basePath: /v1
         paths:
           /pets/{id}:
             get:
               operationId: getPet
               parameters:
                 - name: id
                   in: path
                   required: true
                   type: integer
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            service PetsIdService {
               @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/v1/pets/{id}")
               operation getPet(
                  @PathVariable("id")
                  id : Int
               )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }
}
