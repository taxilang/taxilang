package lang.taxi.generators.openApi.v3

import lang.taxi.generators.openApi.TaxiGenerator
import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class OpenApiTypeMapperTest {

   @Test
   fun `named schema of primitive type`() {

      openApiYaml(
         schemas = """
            Name:
              type: string
         """
      ) generates """
         namespace vyne.openApi {
            type Name inherits String
         }
      """
   }

   @Test
   fun `named schema of intermediate type`() {

      openApiYaml(
         schemas = """
            AdminPassword:
              type: string
              format: password
         """
      ) generates """
         namespace vyne.openApi {
            type password inherits String
            type AdminPassword inherits password
         }
      """
   }

   @Test
   fun `named schema of object type`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  type: string
         """
      ) generates """
         namespace vyne.openApi {
            model Person {
              name: String?
            }
         }
      """
   }

   @Test
   fun `named schema of object type with nested anonymous model`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                address:
                  type: object
                  properties:
                    street:
                      type: string
         """
      ) generates """
         namespace vyne.openApi {
            model AnonymousTypePersonAddress {
              street: String?
            }
            model Person {
              address: AnonymousTypePersonAddress?
            }
         }
      """
   }

   @Test
   fun `named schema of object type with doubly nested anonymous model`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                address:
                  type: object
                  properties:
                    house:
                      type: object
                      properties:
                        number:
                          type: integer
                          format: int32
         """
      ) generates """
         namespace vyne.openApi {
            model AnonymousTypePersonAddressHouse {
              number: Int?
            }
            model AnonymousTypePersonAddress {
              house: AnonymousTypePersonAddressHouse?
            }
            model Person {
              address: AnonymousTypePersonAddress?
            }
         }
      """
   }

   @Test
   fun `named schema of object type with reference to another named schema`() {

      openApiYaml(
         schemas = """
            Person:
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
      ) generates """
         namespace vyne.openApi {
            model Address {
              street: String?
            }
            model Person {
              address: Address?
            }
         }
      """
   }

   @Test
   fun `named schema of object type with recursive reference`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                partner:
                  ${'$'}ref: "#/components/schemas/Person"
         """
      ) generates """
         namespace vyne.openApi {
            model Person {
              partner: Person?
            }
         }
      """
   }

   @Test
   fun `component array of primitive type`() {

      openApiYaml(
         schemas = """
            Strings:
              type: array
              items:
                type: string
         """
      ) generates """
         namespace vyne.openApi {
            type Strings inherits String[]
         }
      """
   }

   @Test
   fun `component array of inline object type`() {

      openApiYaml(
         schemas = """
            People:
              type: array
              items:
                type: object
                properties:
                  name:
                    type: string
         """
      ) generates """
         namespace vyne.openApi {
            model AnonymousTypePeopleElement {
              name : String?
            }
            type People inherits AnonymousTypePeopleElement[]
         }
      """
   }

   @Test
   fun `component array of reference to object type`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  type: string
            People:
              type: array
              items:
                ${'$'}ref: "#/components/schemas/Person"
         """
      ) generates """
         namespace vyne.openApi {
            model Person {
              name : String?
            }
            type People inherits Person[]
         }
      """
   }

   @Test
   fun `component array of array of array of primitive type`() {

      openApiYaml(
         schemas = """
            Strings:
              type: array
              items:
                type: array
                items:
                  type: string
         """
      ) generates """
         namespace vyne.openApi {
            type Strings inherits Array<Array<String>>
         }
      """
   }

   @Test
   fun `component array of array of array of reference to object type`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  type: string
            People:
              type: array
              items:
                type: array
                items:
                  type: object
                  ${'$'}ref: "#/components/schemas/Person"
         """
      ) generates """
         namespace vyne.openApi {
            model Person {
              name : String?
            }
            type People inherits Array<Array<Person>>
         }
      """
   }

   @Test
   fun `inline array of primitive type`() {

      openApiYaml(
         schemas = """
            Organisation:
              type: object
              properties:
                people:
                  type: array
                  items:
                    type: string
         """
      ) generates """
         namespace vyne.openApi {
            model Organisation {
              people : String[]?
            }
         }
      """
   }

   @Test
   fun `inline array of inline object type`() {

      openApiYaml(
         schemas = """
            Organisation:
              type: object
              properties:
                people:
                  type: array
                  items:
                    type: object
                    properties:
                      name:
                        type: string
         """
      ) generates """
         namespace vyne.openApi {
            model AnonymousTypeOrganisationPeopleElement {
              name : String?
            }
            model Organisation {
              people : AnonymousTypeOrganisationPeopleElement[]?
            }
         }
      """
   }

   @Test
   fun `inline array of reference to object type`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  type: string
            Organisation:
              type: object
              properties:
                people:
                  type: array
                  items:
                    ${'$'}ref: "#/components/schemas/Person"
         """
      ) generates """
         namespace vyne.openApi {
            model Person {
              name : String?
            }
            model Organisation {
              people : Person[]?
            }
         }
      """
   }

   @Test
   fun `inline array of array of array of primitive type`() {

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
                        type: string
         """
      ) generates """
         namespace vyne.openApi {
            model Organisation {
              people : Array<Array<Array<String>>>?
            }
         }
      """
   }

   @Test
   fun `inline array of array of array of reference to object type`() {

      openApiYaml(
         schemas = """
           Person:
             type: object
             properties:
               name:
                 type: string
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
                       ${'$'}ref: "#/components/schemas/Person"
         """
      ) generates """
         namespace vyne.openApi {
            model Person {
              name : String?
            }
            model Organisation {
              people : Array<Array<Array<Person>>>?
            }
         }
      """
   }

   @Test
   fun `reference to array of primitive type`() {

      openApiYaml(
         schemas = """
            People:
              type: array
              items:
                type: string
            Organisation:
              type: object
              properties:
                people:
                   ${'$'}ref: "#/components/schemas/People"
         """
      ) generates """
         namespace vyne.openApi {
            type People inherits String[]
            model Organisation {
              people: People?
            }
         }
      """
   }

   @Test
   fun `reference to array of inline object type`() {

      openApiYaml(
         schemas = """
            People:
              type: array
              items:
                type: object
                properties:
                  name:
                    type: string
            Organisation:
              type: object
              properties:
                people:
                   ${'$'}ref: "#/components/schemas/People"
         """
      ) generates """
         namespace vyne.openApi {

            model AnonymousTypePeopleElement {
               name : String?
            }

            type People inherits AnonymousTypePeopleElement[]

            model Organisation {
               people : People?
            }
         }
         """
   }

   @Test
   fun `reference to array of reference to object type`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  type: string
            People:
              type: array
              items:
                ${'$'}ref: "#/components/schemas/Person"
            Organisation:
              type: object
              properties:
                people:
                   ${'$'}ref: "#/components/schemas/People"
         """
      ) generates """
         namespace vyne.openApi {

            model Person {
               name : String?
            }

            type People inherits Person[]

            model Organisation {
               people : People?
            }
         }
         """
   }

   @Test
   fun `reference to array of array of array of primitive type`() {

      openApiYaml(
         schemas = """
            People:
              type: array
              items:
                type: array
                items:
                  type: array
                  items:
                    type: string
            Organisation:
              type: object
              properties:
                people:
                   ${'$'}ref: "#/components/schemas/People"
         """
      ) generates """
         namespace vyne.openApi {

            type People inherits Array<Array<Array<String>>>

            model Organisation {
               people : People?
            }
         }
         """
   }

   @Test
   fun `reference to array of array of array of reference to object type`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  type: string
            People:
              type: array
              items:
                type: array
                items:
                  type: array
                  items:
                    ${'$'}ref: "#/components/schemas/Person"
            Organisation:
              type: object
              properties:
                people:
                   ${'$'}ref: "#/components/schemas/People"
         """
      ) generates """
         namespace vyne.openApi {

            model Person {
               name : String?
            }

            type People inherits Array<Array<Array<Person>>>

            model Organisation {
               people : People?
            }
         }
         """
   }

   private infix fun String.generates(@Language("taxi") expectedTaxi: String) {
      expectToCompileTheSame(
         generated = TaxiGenerator().generateAsStrings(this, "vyne.openApi").taxi,
         expected = expectedTaxi
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
