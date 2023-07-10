package lang.taxi.generators.openApi

import com.winterbe.expekt.expect
import lang.taxi.testing.TestHelpers
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SimpleConversionTest {

    lateinit var generator: TaxiGenerator
    @BeforeEach
    fun setup() {
        generator = TaxiGenerator();
    }

    @Test
    fun canImportOpenApiV3Spec() {
        val source = testResource("/openApiSpec/v3.0/petstore.yaml")
        val taxiDef = generator.generateAsStrings(source, "vyne.openApi")
        expect(taxiDef.taxi).to.be.not.empty
        val expected = """
namespace vyne.openApi {
   model Pet {
      id : Int
      name : String
      tag : String?
   }

   type Pets inherits Pet[]

   model Error {
      code : Int
      message : String
   }

   service PetsService {
      @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/v1/pets")
      operation listPets(
      [[ How many items to return at one time (max 100) ]]
      limit : Int ) : Pets
      @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/v1/pets")
      operation createPets(  )
   }
   service PetsPetIdService {
      @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/v1/pets/{petId}")
      operation showPetById(
      [[ The id of the pet to retrieve ]]
      @PathVariable(value = "petId") petId : String ) : Pets
   }
}
        """.trimIndent()
        TestHelpers.expectToCompileTheSame(taxiDef.taxi, expected)
    }
}


fun testResource(path: String): String {
    val inputStream = SimpleConversionTest::class.java.getResourceAsStream(path)
    try {
        return IOUtils.toString(inputStream)
    } catch (exception: Exception) {
        System.out.println("Cannot import $path : ${exception.message}")
        throw exception
    }
}

fun testFile(path: String): Pair<Filename, Source> {
    return path to testResource(path)
}
typealias Filename = String
typealias Source = String
