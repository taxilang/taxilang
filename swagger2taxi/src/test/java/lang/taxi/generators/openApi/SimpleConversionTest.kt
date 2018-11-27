package lang.taxi.generators.openApi

import com.winterbe.expekt.expect
import lang.taxi.TaxiDocument
import lang.taxi.testing.TestHelpers
import org.apache.commons.io.IOUtils
import org.junit.Before
import org.junit.Test
import java.net.URI

class SimpleConversionTest {

    lateinit var generator: TaxiGenerator
    @Before
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
    fun canConvertPetstoreToTaxi() {
//        val source = IOUtils.toString(URI.create("https://gitlab.com/taxi-lang/taxi-lang/raw/master/swagger2taxi/src/test/resources/openApiSpec/v2.0/yaml/petstore-simple.yaml"))
        val source = testResource("/openApiSpec/v2.0/yaml/petstore-simple.yaml")
        val taxiDef = generator.generateAsStrings(source, "vyne.openApi")

        val expected = """
namespace vyne.openApi  {

    type NewPet {
        name : String
        tag : String
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
        operation findPetById(  id : Int ) : Pet
        @HttpOperation(method = "DELETE" , url = "http://petstore.swagger.io/api/pets/{id}")
        operation deletePet(  id : Int )
    }
}

        """.trimIndent()
        TestHelpers.expectToCompileTheSame(taxiDef.taxi, expected)
    }
}



fun testResource(path:String):String {
    val inputStream = SimpleConversionTest::class.java.getResourceAsStream(path)
    return IOUtils.toString(inputStream)
}