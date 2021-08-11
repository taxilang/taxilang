package lang.taxi.generators.openApi.swagger

import com.winterbe.expekt.should
import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.testing.TestHelpers.compile
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Disabled
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

   @Test @Disabled("inline parameter schemas are not yet supported for swagger")
   fun `inline request body is captured as a param`() {
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
             post:
               parameters:
                 -
                   name: pet
                   description: Pet to add to the store
                   in: body
                   required: true
                   schema:
                     properties:
                       name:
                         type: string
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            model AnonymousTypePostPetsBody {
              name: String?
            }
            service PetsService {
               @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/v1/pets")
               operation PostV1Pets(
                 @RequestBody anonymousTypePostPetsBody : AnonymousTypePostPetsBody
               )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `request body is captured as a param when a reference to a definition`() {
      @Language("yaml")
      val openApiSpec = """
         swagger: "2.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         host: petstore.swagger.io
         basePath: /v1
         definitions:
           NewPet:
             type: object
             properties:
               name:
                 type: string
         paths:
           /pets:
             post:
               parameters:
                 -
                   name: pet
                   description: Pet to add to the store
                   in: body
                   required: true
                   schema:
                       ${'$'}ref: "#/definitions/NewPet"
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            model NewPet {
              name: String?
            }
            service PetsService {
               @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/v1/pets")
               operation PostV1Pets(
                 @RequestBody pet : NewPet
               )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test @Disabled("inline parameter schemas are not yet supported for swagger")
   fun `inline response is captured as a named type`() {
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
             post:
               responses:
                 '200':
                   description: successful operation
                   schema:
                     type: object
                     properties:
                       name:
                         type: string
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            model AnonymousTypePostPets {
              name: String?
            }
            service PetsService {
               @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/v1/pets")
               operation PostV1Pets(): AnonymousTypePostPets
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `response as a reference to a definition`() {
      @Language("yaml")
      val openApiSpec = """
         swagger: "2.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         host: petstore.swagger.io
         basePath: /v1
         definitions:
           Pet:
             type: object
             properties:
               name:
                 type: string
         paths:
           /pets:
             post:
               responses:
                 '200':
                   description: successful operation
                   schema:
                     ${'$'}ref: "#/definitions/Pet"
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            model Pet {
              name: String?
            }
            service PetsService {
               @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/v1/pets")
               operation PostV1Pets(): Pet
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }
}
