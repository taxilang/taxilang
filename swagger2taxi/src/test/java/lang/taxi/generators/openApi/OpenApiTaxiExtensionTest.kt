package lang.taxi.generators.openApi

import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.junit.jupiter.api.Test

class OpenApiTaxiExtensionTest {

   @Test
   fun `an openapi document with x-taxi-type extensions can resolve to specific taxi types`() {
      val openApiWithXTaxiType = testResource("/openApiSpec/v3.0/petstore-expanded-x-taxi-types.yaml")

      val taxiDef = TaxiGenerator().generateAsStrings(openApiWithXTaxiType, "vyne.openApi")

      val imaginaryExistingTaxonomy = """
         namespace petstore {

            type Id inherits String

            type PetId inherits Id

            type Name inherits String

            model Pet {
              id : PetId
              name : Name?
              tag : String
            }
         }
         """.trimIndent()

      expectToCompileTheSame(
         generated = listOf(imaginaryExistingTaxonomy) + taxiDef.taxi,
         expected = listOf(
            imaginaryExistingTaxonomy,
            """
            import petstore.Pet
            import petstore.PetId
            import petstore.Name

            namespace vyne.openApi {

               type ErrorCode inherits Int

               model NewPet {
                  name : Name?
                  tag : String
               }

               model Error {
                  code : ErrorCode?
                  message : String?
               }

               service PetsService {
                  @HttpOperation(method = "GET" , url = "/pets")
                  operation findPets(  tags : String[],  limit : Int ) : Pet[]
                  @HttpOperation(method = "POST" , url = "/pets")
                  operation addPet(   ) : Pet
               }
               service PetsIdService {
                  @HttpOperation(method = "GET" , url = "/pets/{id}")
                  operation find_pet_by_id(  id : PetId ) : Pet
                  @HttpOperation(method = "DELETE" , url = "/pets/{id}")
                  operation deletePet(  id : PetId )
               }
            }
            """.trimIndent()
         )
      )
   }
}
