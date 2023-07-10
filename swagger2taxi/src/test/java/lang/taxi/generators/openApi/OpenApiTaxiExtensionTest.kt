package lang.taxi.generators.openApi

import lang.taxi.testing.TestHelpers.expectToCompileTheSame
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class OpenApiTaxiExtensionTest {

   @TestFactory
   fun `an openapi document with x-taxi-type extensions can resolve to specific taxi types`(): List<DynamicTest> =
      listOf(
         testFile("/openApiSpec/v3.0/petstore-expanded-x-taxi-types.yaml"),
         testFile("/openApiSpec/v3.0/petstore-expanded-x-taxi-types.json"),
      ).map { (fileName, openApiWithXTaxiType) ->
         dynamicTest("can resolve x-taxi-type extensions in $fileName") {

            val taxiDef = TaxiGenerator().generateAsStrings(
               openApiWithXTaxiType,
               "vyne.openApi"
            )

            val imaginaryExistingTaxonomy = """
               namespace petstore {

                  type Id inherits String

                  type PetId inherits Id

                  type Name inherits String

                  type Tag inherits String

                  model Pet {
                    id : PetId
                    name : Name
                    tag : Tag
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
                  import petstore.Tag

                  namespace vyne.openApi {

                     type ErrorCode inherits Int

                     model NewPet {
                        name : Name
                        tag : Tag?
                     }

                     model Error {
                        code : ErrorCode
                        message : String
                     }

                    service PetsService {
                        [[ Returns all pets from the system that the user has access to
                        Nam sed condimentum est. Maecenas tempor sagittis sapien, nec rhoncus sem sagittis sit amet. Aenean at gravida augue, ac iaculis sem. Curabitur odio lorem, ornare eget elementum nec, cursus id lectus. Duis mi turpis, pulvinar ac eros ac, tincidunt varius justo. In hac habitasse platea dictumst. Integer at adipiscing ante, a sagittis ligula. Aenean pharetra tempor ante molestie imperdiet. Vivamus id aliquam diam. Cras quis velit non tortor eleifend sagittis. Praesent at enim pharetra urna volutpat venenatis eget eget mauris. In eleifend fermentum facilisis. Praesent enim enim, gravida ac sodales sed, placerat id erat. Suspendisse lacus dolor, consectetur non augue vel, vehicula interdum libero. Morbi euismod sagittis libero sed lacinia.
                        Sed tempus felis lobortis leo pulvinar rutrum. Nam mattis velit nisl, eu condimentum ligula luctus nec. Phasellus semper velit eget aliquet faucibus. In a mattis elit. Phasellus vel urna viverra, condimentum lorem id, rhoncus nibh. Ut pellentesque posuere elementum. Sed a varius odio. Morbi rhoncus ligula libero, vel eleifend nunc tristique vitae. Fusce et sem dui. Aenean nec scelerisque tortor. Fusce malesuada accumsan magna vel tempus. Quisque mollis felis eu dolor tristique, sit amet auctor felis gravida. Sed libero lorem, molestie sed nisl in, accumsan tempor nisi. Fusce sollicitudin massa ut lacinia mattis. Sed vel eleifend lorem. Pellentesque vitae felis pretium, pulvinar elit eu, euismod sapien. ]]
                        @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/api/pets")
                        operation findPets(
                        [[ tags to filter by ]]
                        tags : petstore.Tag[],
                        [[ maximum number of results to return ]]
                        limit : Int ) : petstore.Pet[]
                        [[ Creates a new pet in the store.  Duplicates are allowed ]]
                        @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/api/pets")
                        operation addPet( @RequestBody newPet : NewPet ) : petstore.Pet
                     }
                     service PetsIdService {
                        [[ Returns a user based on a single ID, if the user does not have access to the pet ]]
                        @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/api/pets/{id}")
                        operation find_pet_by_id(
                        [[ ID of pet to fetch ]]
                        @PathVariable(value = "id") id : petstore.PetId ) : petstore.Pet
                        [[ deletes a single pet based on the ID supplied ]]
                        @HttpOperation(method = "DELETE" , url = "http://petstore.swagger.io/api/pets/{id}")
                        operation deletePet(
                        [[ ID of pet to delete ]]
                        @PathVariable(value = "id") id : petstore.PetId )
                     }
                  }
                  """.trimIndent()
               )
            )
         }
      }
}
