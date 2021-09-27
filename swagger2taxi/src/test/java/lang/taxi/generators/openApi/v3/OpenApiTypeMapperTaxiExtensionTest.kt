package lang.taxi.generators.openApi.v3

import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class OpenApiTypeMapperTaxiExtensionTest {

   @Test
   fun `named schema of primitive type, create = false`() {

      openApiYaml(
         schemas = """
            Name:
              x-taxi-type:
                name: org.example.TaxiName
              type: string
         """
      ) generates """
      """
   }

   @Test
   fun `named schema of primitive type, create = true`() {

      openApiYaml(
         schemas = """
            Name:
              x-taxi-type:
                name: org.example.TaxiName
                create: true
              type: string
         """
      ) generates """
         namespace org.example {
            type TaxiName inherits String
         }
      """
   }

   @Test
   fun `named schema of intermediate type, create = false`() {

      openApiYaml(
         schemas = """
            AdminPassword:
              x-taxi-type:
                name: org.example.Password
              type: string
              format: password
         """
      ) generates """
      """
   }

   @Test
   fun `named schema of intermediate type, create = true`() {

      openApiYaml(
         schemas = """
            AdminPassword:
              x-taxi-type:
                name: org.example.Password
                create: true
              type: string
              format: password
         """
      ) generates listOf("""
         namespace vyne.openApi {
            type password inherits String
         }
         """,
         """
         namespace org.example {
            type Password inherits vyne.openApi.password
         }
      """)
   }

   @Test
   fun `named schema of object type, create = false`() {

      openApiYaml(
         schemas = """
            Person:
              x-taxi-type:
                name: org.example.Person
                create: false
              properties:
                name:
                  type: string
         """
      ) generates """
      """
   }

   @Test
   fun `named schema of object type, create = true`() {

      openApiYaml(
         schemas = """
            Person:
              x-taxi-type:
                name: org.example.Person
              properties:
                name:
                  type: string
         """
      ) generates """
         namespace org.example {
            model Person {
              name: String
            }
         }
      """
   }

   @Test
   fun `named schema of object type with nested anonymous model, create = true`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                address:
                  x-taxi-type:
                    name: Address
                  properties:
                    street:
                      type: string
         """
      ) generates """
         namespace vyne.openApi {
            model Address {
              street: String
            }
            model Person {
              address: Address
            }
         }
      """
   }

   @Test
   fun `named schema of object type with nested anonymous model, create = false`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                address:
                  x-taxi-type:
                    name: org.other.Address
                    create: false
                  properties:
                    street:
                      type: string
         """
      ) generates """
         import org.other.Address

         namespace vyne.openApi {
            model Person {
              address: Address
            }
         }
      """
   }

   @Test
   fun `named schema of object type with field of primitive type with x-taxi-type, create = true`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  x-taxi-type:
                    name: Name
                    create: true
                  type: string
         """
      ) generates """

         namespace vyne.openApi {
            type Name inherits String
            model Person {
               name : Name
            }
         }
      """
   }

   @Test
   fun `named schema of object type with field of primitive type with x-taxi-type, create = false`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  x-taxi-type:
                    name: org.other.Name
                  type: string
         """
      ) generates """
         import org.other.Name

         namespace vyne.openApi {
            model Person {
               name : Name
            }
         }
      """
   }

   @Test
   fun `named schema of object type with doubly nested anonymous model, create = true`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                address:
                  type: object
                  properties:
                    house:
                      x-taxi-type:
                        name: House
                      type: object
                      properties:
                        number:
                          type: integer
                          format: int32
         """
      ) generates """
         namespace vyne.openApi {
            model House {
              number: Int
            }
            model AnonymousTypePersonAddress {
              house: House
            }
            model Person {
              address: AnonymousTypePersonAddress
            }
         }
      """
   }

   @Test
   fun `named schema of object type with doubly nested anonymous model, create = false`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                address:
                  type: object
                  properties:
                    house:
                      x-taxi-type:
                        name: org.other.House
                        create: false
                      type: object
                      properties:
                        number:
                          type: integer
                          format: int32
         """
      ) generates """
         import org.other.House

         namespace vyne.openApi {
            model AnonymousTypePersonAddress {
              house: House
            }
            model Person {
              address: AnonymousTypePersonAddress
            }
         }
      """
   }

   @Test
   fun `named schema of object type with reference to another named schema, create = true`() {

      openApiYaml(
         schemas = """
            Person:
              x-taxi-type:
                name: example.Person
              type: object
              properties:
                address:
                  ${'$'}ref: "#/components/schemas/Address"
            Address:
              type: object
              properties:
                street:
                  type: string
         """
      ) generates listOf("""
         import vyne.openApi.Address
         namespace example {
            model Person {
              address: Address
            }
         }
         """,
         """
         namespace vyne.openApi {
            model Address {
              street: String
            }

         }
         """)
   }

   @Test
   fun `named schema of object type with reference to another named schema, other schema create = true`() {

      openApiYaml(
         schemas = """
            Person:
              x-taxi-type:
                name: example.Person
              type: object
              properties:
                address:
                  ${'$'}ref: "#/components/schemas/Address"
            Address:
              x-taxi-type:
                name: example.Address
              type: object
              properties:
                street:
                  type: string
         """
      ) generates """
         namespace example {
            model Address {
              street: String
            }
            model Person {
              address: Address
            }
         }
      """
   }

   @Test
   fun `component array of inline object type, create = true`() {

      openApiYaml(
         schemas = """
            People:
              type: array
              items:
                x-taxi-type:
                  name: example.Person
                type: object
                properties:
                  name:
                    type: string
         """
      ) generates listOf(
         """
         namespace example {
            model Person {
              name : String
            }
         }
         """,
         """
         import example.Person
         namespace vyne.openApi {
            type People inherits Person[]
         }
         """)
   }

   @Test
   fun `component array of inline object type, create = false`() {

      openApiYaml(
         schemas = """
            People:
              type: array
              items:
                x-taxi-type:
                  name: org.other.Person
                  create: false
                type: object
                properties:
                  name:
                    type: string
         """
      ) generates """
         import org.other.Person
         namespace vyne.openApi {
            type People inherits Person[]
         }
         """
   }

   @Test
   fun `inline array of array of array of primitive type, create = false`() {

      openApiYaml(
         schemas = """
            Organisation:
              type: object
              properties:
                people:
                  type: array
                  items:
                    type: array
                    items:
                      type: array
                      items:
                        x-taxi-type:
                          name: org.other.Name
                        type: string
         """
      ) generates """
         import org.other.Name

         namespace vyne.openApi {
            model Organisation {
              people : Array<Array<Array<Name>>>
            }
         }
      """
   }

   @Test
   fun `inline array of array of array of primitive type, create = true`() {

      openApiYaml(
         schemas = """
            Organisation:
              type: object
              properties:
                people:
                  type: array
                  items:
                    type: array
                    items:
                      type: array
                      items:
                        x-taxi-type:
                          name: Name
                          create: true
                        type: string
         """
      ) generates
         """
         namespace vyne.openApi {

            type Name inherits String

            model Organisation {
              people : Array<Array<Array<Name>>>
            }
         }
      """
   }

   @Test
   fun `service return type, create = true`() {
      openApiYaml(
         paths = """
            /people:
              get:
                responses:
                  '200':
                    content:
                      application/json:
                        schema:
                          type: array
                          items:
                            x-taxi-type:
                              name: Person
                            type: object
                            properties:
                              name:
                                x-taxi-type:
                                  name: Name
                                  create: true
                                type: string
         """
      ) generates
         """
         namespace vyne.openApi {

            type Name inherits String

            model Person {
               name : Name
            }

            service PeopleService {
               @HttpOperation(method = "GET" , url = "/people")
               operation GetPeople(  ) : Person[]
            }
         }
      """
   }

   @Test
   fun `service return type, create = false`() {
      openApiYaml(
         paths = """
            /people:
              get:
                responses:
                  '200':
                    content:
                      application/json:
                        schema:
                          type: array
                          items:
                            x-taxi-type:
                              name: org.other.Person
                              create: false
                            type: object
                            properties:
                              name:
                                x-taxi-type:
                                  name: Name
                                type: string
         """
      ) generates
         """
         import org.other.Person

         namespace vyne.openApi {

            service PeopleService {
               @HttpOperation(method = "GET" , url = "/people")
               operation GetPeople(  ) : Person[]
            }
         }
      """
   }

   @Test
   fun `service request body, create = true`() {
      openApiYaml(
         paths = """
            /people:
              post:
                requestBody:
                  required: true
                  content:
                    application/json:
                      schema:
                        x-taxi-type:
                          name: Person
                        type: object
                        properties:
                          name:
                            x-taxi-type:
                              name: Name
                              create: true
                            type: string
                responses:
                  '200': {}
         """
      ) generates
         """
         namespace vyne.openApi {

            type Name inherits String

            model Person {
               name : Name
            }

            service PeopleService {
               @HttpOperation(method = "POST" , url = "/people")
               operation PostPeople( @RequestBody person : Person )
            }
         }
      """
   }

   @Test
   fun `service request body, create = false`() {
      openApiYaml(
         paths = """
            /people:
              post:
                requestBody:
                  required: true
                  content:
                    application/json:
                      schema:
                        x-taxi-type:
                          name: org.other.Person
                          create: false
                        type: object
                        properties:
                          name:
                            x-taxi-type:
                              name: Name
                            type: string
                responses:
                  '200': {}
         """
      ) generates
         """
         import org.other.Person

         namespace vyne.openApi {

            service PeopleService {
               @HttpOperation(method = "POST" , url = "/people")
               operation PostPeople( @RequestBody person : Person )
            }
         }
      """
   }

   @Test
   fun `service param, create = true`() {
      openApiYaml(
         paths = """
            /people/{id}:
              get:
                parameters:
                  - name: id
                    in: path
                    required: true
                    schema:
                      x-taxi-type:
                        name: PersonId
                        create: true
                      type: string
                responses:
                  '200': {}
         """
      ) generates """
         namespace vyne.openApi {

            type PersonId inherits String

            service PeopleIdService {
               @HttpOperation(method = "GET" , url = "/people/{id}")
               operation GetPeopleId( @PathVariable(value = "id") id : PersonId )
            }
         }
      """
   }

   @Test
   fun `service param, create = false`() {
      openApiYaml(
         paths = """
            /people/{id}:
              get:
                parameters:
                  - name: id
                    in: path
                    required: true
                    schema:
                      x-taxi-type:
                        name: org.other.PersonId
                      type: string
                responses:
                  '200': {}
         """
      ) generates """
         import org.other.PersonId

         namespace vyne.openApi {

            service PeopleIdService {
               @HttpOperation(method = "GET" , url = "/people/{id}")
               operation GetPeopleId( @PathVariable(value = "id") id : PersonId )
            }
         }
      """
   }

   private val otherTaxi = """
      namespace org.other {
        type Person
        type PersonId
        type Name
        type Address
        type House
      }
   """.trimIndent()

   private infix fun String.generates(@Language("taxi") expectedTaxi: String) {
      expectToCompileTheSame(
         generated = TaxiGenerator().generateAsStrings(this, "vyne.openApi").taxi + otherTaxi,
         expected = listOf(expectedTaxi, otherTaxi)
      )
   }

   private infix fun String.generates(@Language("taxi") expectedTaxi: List<String>) {
      expectToCompileTheSame(
         generated = TaxiGenerator().generateAsStrings(this, "vyne.openApi").taxi + otherTaxi,
         expected = expectedTaxi + otherTaxi
      )
   }

   @Language("yaml")
   private fun openApiYaml(
      @Language("yaml")
      schemas: String? = null,
      @Language("yaml")
      paths: String? = null
   ): String {
      fun yamlObjectProperty(propName: String, propValue: String?) = if (propValue == null) {
         "|$propName: {}"
      } else {
         """
           |$propName:
           ${propValue.replaceIndent("|  ")}
         """
      }.trimMargin()
      return """
        |openapi: "3.0.0"
        |info:
        |  version: 1.0.0
        |  title: Swagger Petstore
        |components:
           ${yamlObjectProperty("schemas", schemas).replaceIndent("|  ")}
         ${yamlObjectProperty("paths", paths).replaceIndent("|")}
        """.trimMargin()
   }
}
