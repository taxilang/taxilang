package lang.taxi.generators.openApi

import org.apache.commons.io.IOUtils
import org.junit.Before
import org.junit.Test

class SimpleConversionTest {

    lateinit var generator: TaxiGenerator
    @Before
    fun setup() {
        generator = TaxiGenerator();
    }
    @Test
    fun canConvertPetstoreToTaxi() {
        val source = testResource("/openApiSpec/v2.0/yaml/petstore-simple.yaml")
        val generated = generator.generateAsStrings(source)
        TODO()
    }
}



fun testResource(path:String):String {
    val inputStream = SimpleConversionTest::class.java.getResourceAsStream(path)
    return IOUtils.toString(inputStream)
}