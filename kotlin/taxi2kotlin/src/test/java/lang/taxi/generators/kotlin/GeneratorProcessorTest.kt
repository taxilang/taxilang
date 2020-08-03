package lang.taxi.generators.kotlin

import com.nhaarman.mockitokotlin2.mock
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
        val expected = """import kotlin.Int
import lang.taxi.annotations.DataType
import lang.taxi.generators.kotlin.Marker
import taxi.generated.TypeNames.Person

@DataType(
  value = Person,
  imported = true
)
open class Person(
  @Marker
  val id: Int
)

package taxi.generated

import kotlin.String

object TypeNames {
  const val Person: String = "Person"
}""".trimNewLines()

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
        val expected = """import kotlin.Int
import lang.taxi.annotations.DataType
import lang.taxi.generators.kotlin.Marker
import taxi.generated.TypeNames.Person

@DataType(
  value = Person,
  imported = true
)
@Marker
open class Person(
  val id: Int
)

package taxi.generated

import kotlin.String

object TypeNames {
  const val Person: String = "Person"
}""".trimNewLines()

        expect(output).to.equal(expected)

    }

    private fun compileAndGenerate(taxi: String, processors: List<Processor> = defaultProcessors): String {
        val taxiDoc = Compiler.forStrings(taxi).compile()
        val output = KotlinGenerator().generate(taxiDoc,processors, mock {  })
        return output.joinToString("\n") { it.content }
    }
}

annotation class Marker
