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
      ) shouldGenerate """
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
      ) shouldGenerate """
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Person {
              name: String
            }
         }
      """
   }

   @Test
   fun `nullable properties in openApi schema become nullable in taxi`() {

      openApiYaml(
         schemas = """
            Person:
              type: object
              properties:
                name:
                  type: string
                  nullable: true
         """
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Person {
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model AnonymousTypePersonAddress {
              street: String
            }
            closed model Person {
              address: AnonymousTypePersonAddress
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model AnonymousTypePersonAddressHouse {
              number: Int
            }
            closed model AnonymousTypePersonAddress {
              house: AnonymousTypePersonAddressHouse
            }
            closed model Person {
              address: AnonymousTypePersonAddress
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Address {
              street: String
            }
            closed model Person {
              address: Address
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Person {
              partner: Person
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
      ) shouldGenerate """
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model AnonymousTypePeopleElement {
              name : String
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Person {
              name : String
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
      ) shouldGenerate """
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Person {
              name : String
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Organisation {
              people : String[]
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model AnonymousTypeOrganisationPeopleElement {
              name : String
            }
            closed model Organisation {
              people : AnonymousTypeOrganisationPeopleElement[]
            }
         }
      """
   }

   @Test
   fun `enum field on object type`() {
      openApiYaml(
         schemas = """
            Pet:
               type: object
               properties:
                  id:
                     type: string
                  name:
                     type: string
                  petType:
                     type: string
                     enum:
                        - dog
                        - cat
                        - bird
                        - fish
               required:
                  - id
                  - name
                  - type
         """
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Pet {
               id : String
               name : String
               petType : PetPetType?
            }
            enum PetPetType {
               dog,
               cat,
               bird,
               fish
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Person {
              name : String
            }
            closed model Organisation {
              people : Person[]
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Organisation {
              people : Array<Array<Array<String>>>
            }
         }
      """
   }

   @Test
   fun `a type reference to an enum`() {
      openApiYaml(
         schemas = """
          PetType:
            type: string
            enum:
              - dog
              - cat
              - killer-whale
              - bird
              - fish
          Pet:
            type: object
            properties:
              id:
                type: string
              name:
                type: string
              petType:
                ${'$'}ref: '#/components/schemas/PetType'
            required:
              - id
              - name
              - type
          Owner:
            type: object
            properties:
              id:
                type: string
              name:
                type: string
              petTypePreference:
                ${'$'}ref: '#/components/schemas/PetType'
            required:
              - id
              - name
              - petTypePreference

"""
      )  shouldGenerate """
namespace vyne.openApi {
   enum PetType {
      dog,
      cat,
      `killer-whale`,
      bird,
      fish
   }

   closed model Pet {
      id : String
      name : String
      petType : PetType?
   }

   closed model Owner {
      id : String
      name : String
      petTypePreference : PetType
   }
}"""
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
      ) shouldGenerate """
         namespace vyne.openApi {
            closed model Person {
              name : String
            }
            closed model Organisation {
              people : Array<Array<Array<Person>>>
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
      ) shouldGenerate """
         namespace vyne.openApi {
            type People inherits String[]
            closed model Organisation {
              people: People
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
      ) shouldGenerate """
         namespace vyne.openApi {

            closed model AnonymousTypePeopleElement {
               name : String
            }

            type People inherits AnonymousTypePeopleElement[]

            closed model Organisation {
               people : People
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
      ) shouldGenerate """
         namespace vyne.openApi {

            closed model Person {
               name : String
            }

            type People inherits Person[]

            closed model Organisation {
               people : People
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
      ) shouldGenerate """
         namespace vyne.openApi {

            type People inherits Array<Array<Array<String>>>

            closed model Organisation {
               people : People
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
      ) shouldGenerate """
         namespace vyne.openApi {

            closed model Person {
               name : String
            }

            type People inherits Array<Array<Array<Person>>>

            closed model Organisation {
               people : People
            }
         }
         """
   }

   @Test
   fun `component with allOf`() {
      openApiYaml(
         schemas = """
            Named:
              type: object
              properties:
                name:
                  type: string
            Organisation:
              allOf:
                - ${'$'}ref: "#/components/schemas/Named"
                - properties:
                    age:
                      type: integer
         """
      ) shouldGenerate """
         namespace vyne.openApi {

            closed model Named {
               name : String
            }

            closed model Organisation inherits Named {
              age: Int
            }
         }
         """
   }

   @Test
   fun `component with field with allOf`() {
      openApiYaml(
         schemas = """
            Group:
              type: object
              properties:
                name:
                  type: string
            Person:
              properties:
                group:
                  allOf:
                    - ${'$'}ref: "#/components/schemas/Group"
         """
      ) shouldGenerate """
         namespace vyne.openApi {

            closed model Group {
               name : String
            }

            closed model AnonymousTypePersonGroup inherits Group

            closed model Person {
               group : AnonymousTypePersonGroup
            }
         }
         """
   }

   private infix fun String.shouldGenerate(@Language("taxi") expectedTaxi: String) {
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
