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
    fun canConvertFullPetstoreApiToTaxi() {
        val source = testResource("/openApiSpec/v2.0/json/pets.json")
        val taxiDef = generator.generateAsStrings(source, "vyne.openApi")
        expect(taxiDef.taxi).to.be.not.`null`
        // Should compile

    }

    @Test
    fun canImportPetstore() {
        val source = testResource("/openApiSpec/v2.0/json/petstore.json")
        val taxiDef = generator.generateAsStrings(source, "vyne.openApi")

        val expected = """
namespace vyne.openApi {

   type Pet {
      id : Int
      name : String
      tag : String?
   }

   type Error {
      code : Int
      message : String
   }

   service PetsService {
      @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/v1/pets")
      operation listPets(  limit : Int ) : Pet[]
      @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/v1/pets")
      operation createPets(  )
   }
   service PetsPetIdService {
      @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/v1/pets/{petId}")
      operation showPetById(  @PathVariable("petId") petId : String ) : Pet[]
   }
}

        """.trimIndent()
        TestHelpers.expectToCompileTheSame(taxiDef.taxi, expected)
    }

    @Test
    fun canConvertPetstoreSimpleToTaxi() {
//        val source = IOUtils.toString(URI.create("https://gitlab.com/taxi-lang/taxi-lang/raw/master/swagger2taxi/src/test/resources/openApiSpec/v2.0/yaml/petstore-simple.yaml"))
        val source = testResource("/openApiSpec/v2.0/yaml/petstore-simple.yaml")
        val taxiDef = generator.generateAsStrings(source, "vyne.openApi")

        val expected = """
namespace vyne.openApi  {

    type NewPet {
        name : String
        tag : String?
    }

     type Pet inherits NewPet {
        id : Int
    }

     type ErrorModel {
        code : Int
        message : String
    }

    service PetsService {
        @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/api/pets")
        operation findPets(  tags : String,  limit : Int ) : Pet[]
        @HttpOperation(method = "POST" , url = "http://petstore.swagger.io/api/pets")
        operation addPet( @RequestBody pet : NewPet ) : Pet
    }
    service PetsIdService {
        @HttpOperation(method = "GET" , url = "http://petstore.swagger.io/api/pets/{id}")
        operation findPetById(  @PathVariable("id") id : Int ) : Pet
        @HttpOperation(method = "DELETE" , url = "http://petstore.swagger.io/api/pets/{id}")
        operation deletePet(  @PathVariable("id") id : Int )
    }
}

        """.trimIndent()
        TestHelpers.expectToCompileTheSame(taxiDef.taxi, expected)
    }

    @Test
    fun canImportOpenApiV3Spec() {
        val source = testResource("/openApiSpec/v3.0/petstore.yaml")
        val taxiDef = generator.generateAsStrings(source, "vyne.openApi")
        expect(taxiDef.taxi).to.be.not.empty
        val expected = """
namespace vyne.openApi {

   type Pet {
      id : Int?
      name : String?
      tag : String
   }

   type Error {
      code : Int?
      message : String?
   }

   @ServiceDiscoveryClient(serviceName = "http://petstore.swagger.io/v1")
   service PetsService {
      @HttpOperation(method = "GET" , url = "/pets")
      operation listPets(  limit : Int ) : Pet[]
      @HttpOperation(method = "POST" , url = "/pets")
      operation createPets(  )
   }

   @ServiceDiscoveryClient(serviceName = "http://petstore.swagger.io/v1")
   service PetsPetIdService {
      @HttpOperation(method = "GET" , url = "/pets/{petId}")
      operation showPetById( @PathVariable("petId")  petId : String ) : Pet[]
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
