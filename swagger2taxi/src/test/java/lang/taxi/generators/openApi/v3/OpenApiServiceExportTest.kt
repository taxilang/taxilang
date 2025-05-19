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
               @taxi.http.HttpOperation(method = "GET" , url = "/pets")
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
         paths:
           /pets-pets:
             get:
               operationId: find pets
               parameters:
                 - name: pet_limit
                   in: query
                   required: false
                   schema:
                     type: integer
                 - name: x-api-interaction-id
                   in: header
                   required: false
                   schema:
                     type: string
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            service Pets_petsService {
               @taxi.http.HttpOperation(method = "GET" , url = "/pets-pets")
               operation find_pets( @taxi.http.QueryVariable(value = "pet_limit") pet_limit : Int?, @taxi.http.HttpHeader(name = "x-api-interaction-id") x_api_interaction_id : String? )
            }
         }
      """.trimIndent()

      val taxiDef = TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `docs on param arguments are copied across`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             get:
               operationId: findPets
               parameters:
                 - name: pet_limit
                   in: query
                   required: false
                   schema:
                     type: integer
                     description: The upper limit to the number of pets you can possibly tolerate
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            service PetsService {
               @taxi.http.HttpOperation(method = "GET" , url = "/pets")
               operation findPets(
                  [[ The upper limit to the number of pets you can possibly tolerate ]]
                  @taxi.http.QueryVariable(value = "pet_limit") pet_limit : Int?
               )
            }
         }
      """.trimIndent()

      val taxiDef = TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }


   @Test
   fun `path parameters are annotated correctly`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets/{id}:
             get:
               operationId: getPet
               parameters:
                 - name: id
                   in: path
                   required: true
                   schema:
                     type: integer
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            service PetsIdService {
               @taxi.http.HttpOperation(method = "GET" , url = "/pets/{id}")
               operation getPet(
                  @taxi.http.PathVariable("id")
                  id : Int
               )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }


   @Test
   fun `request body is captured as a param`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             post:
               requestBody:
                 description: Pet to add to the store
                 required: true
                 content:
                   application/json:
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
            parameter model AnonymousTypePostPetsBody {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "POST" , url = "/pets")
               operation PostPets(
                 @taxi.http.RequestBody anonymousTypePostPetsBody : AnonymousTypePostPetsBody?
               )
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `request body is captured as a param when a declared schema`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         components:
           schemas:
             NewPet:
               type: object
               properties:
                 name:
                   type: string
         paths:
           /pets:
             post:
               requestBody:
                 description: Pet to add to the store
                 required: true
                 content:
                   application/json:
                     schema:
                       ${'$'}ref: "#/components/schemas/NewPet"
               responses:
                 '200':
                   description: pet response
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed parameter model NewPet {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "POST" , url = "/pets")
               operation PostPets(
                 @taxi.http.RequestBody newPet : NewPet?
               )
            }
         }
      """.trimIndent()


      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `inline response is captured as a named type`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             post:
               responses:
                 '200':
                   description: successful operation
                   content:
                     application/json:
                       schema:
                         type: object
                         properties:
                           name:
                             type: string
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed model AnonymousTypePostPets {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "POST" , url = "/pets")
               operation PostPets(): AnonymousTypePostPets
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `service can be declared as a write operation`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             post:
               x-taxi-operation-kind: write
               responses:
                 '200':
                   description: successful operation
                   content:
                     application/json:
                       schema:
                         type: object
                         properties:
                           name:
                             type: string
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed model AnonymousTypePostPets {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "POST" , url = "/pets")
               write operation PostPets(): AnonymousTypePostPets
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `response as a reference to a component schema`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         components:
           schemas:
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
                   content:
                     application/json:
                       schema:
                         ${'$'}ref: "#/components/schemas/Pet"
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed model Pet {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "POST" , url = "/pets")
               operation PostPets(): Pet
            }
         }
      """.trimIndent()

      val taxiDef =  TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `POST operations are write by default`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             post:
               responses:
                 '200':
                   description: successful operation
                   content:
                     application/json:
                       schema:
                         type: object
                         properties:
                           name:
                             type: string
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed model AnonymousTypePostPets {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "POST" , url = "/pets")
               write operation PostPets(): AnonymousTypePostPets
            }
         }
      """.trimIndent()

      val taxiDef = TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `GET is read by default`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             get:
               operationId: findPets
               responses:
                 '200':
                   description: pet response
                   content:
                     application/json:
                       schema:
                         type: object
                         properties:
                           name:
                             type: string
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed model AnonymousTypeFindPets {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "GET" , url = "/pets")
               operation findPets(): AnonymousTypeFindPets
            }
         }
      """.trimIndent()

      val taxiDef = TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `GET can be overridden to write using x-taxi-operation-kind`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             get:
               x-taxi-operation-kind: write
               operationId: findPets
               responses:
                 '200':
                   description: pet response
                   content:
                     application/json:
                       schema:
                         type: object
                         properties:
                           name:
                             type: string
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed model AnonymousTypeFindPets {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "GET" , url = "/pets")
               write operation findPets(): AnonymousTypeFindPets
            }
         }
      """.trimIndent()

      val taxiDef = TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }

   @Test
   fun `POST can be overridden to read using x-taxi-operation-kind`() {
      @Language("yaml")
      val openApiSpec = """
         openapi: "3.0.0"
         info:
           version: 1.0.0
           title: Swagger Petstore
         paths:
           /pets:
             post:
               x-taxi-operation-kind: read
               operationId: findPets
               responses:
                 '200':
                   description: pet response
                   content:
                     application/json:
                       schema:
                         type: object
                         properties:
                           name:
                             type: string
      """.trimIndent()

      val expectedTaxi = """
         namespace vyne.openApi {
            closed model AnonymousTypeFindPets {
              name: String
            }
            service PetsService {
               @taxi.http.HttpOperation(method = "POST" , url = "/pets")
               operation findPets(): AnonymousTypeFindPets
            }
         }
      """.trimIndent()

      val taxiDef = TaxiGenerator().generateAsStrings(openApiSpec, "vyne.openApi")

      expectToCompileTheSame(taxiDef.taxi, expectedTaxi)
   }
}
