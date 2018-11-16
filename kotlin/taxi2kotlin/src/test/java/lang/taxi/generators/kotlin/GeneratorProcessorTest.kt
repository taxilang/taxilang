package lang.taxi.generators.kotlin

import com.winterbe.expekt.expect
import lang.taxi.Compiler
import lang.taxi.generators.Processor
import org.junit.Test

class GeneratorProcessorTest {

    val defaultProcessors = listOf<Processor>(
            FieldAnnotationInjector("Marker", AnnotationFactories.forType<Marker>()),
            TypeAnnotationInjector("Marker", AnnotationFactories.forType<Marker>())
    )

    @Test
    fun propertyProcessorIsInvoked() {
        val taxi = """
type Person {
    @Marker
    id : Int
}
        """.trimIndent()

        val output = compileAndGenerate(taxi).trimNewLines()
        val expected = """
import kotlin.Int
import lang.taxi.generators.kotlin.Marker

data class Person(@Marker val id: Int)
        """.trimNewLines()

        expect(output).to.equal(expected)
    }

    @Test
    fun typeProcessorInInvoked() {
        val taxi = """
@Marker
type Person {
    id : Int
}
        """.trimIndent()

        val output = compileAndGenerate(taxi).trimNewLines()
        val expected = """
import kotlin.Int
import lang.taxi.generators.kotlin.Marker

@Marker
data class Person(val id: Int)
        """.trimNewLines()

        expect(output).to.equal(expected)

    }

    private fun compileAndGenerate(taxi: String, processors: List<Processor> = defaultProcessors): String {
        val taxiDoc = Compiler.forStrings(taxi).compile()
        val output = KotlinGenerator().generate(taxiDoc,processors)
        return output.joinToString("\n") { it.content }
    }
}

annotation class Marker