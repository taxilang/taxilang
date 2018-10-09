package lang.taxi.generators.kotlin

import com.winterbe.expekt.expect
import lang.taxi.Compiler
import org.junit.Test

class KotlinGeneratorTest {

    @Test
    fun generatesSimpleDataClassFromType() {
        val taxi = """
type Person {
    firstName : String
    lastName : String
    age : Int
    living : Boolean
}
        """.trimIndent()

        val output = compileAndGenerate(taxi).trimNewLines()
        val expected = """
import kotlin.Boolean
import kotlin.Int
import kotlin.String

data class Person(
    val firstName: String,
    val lastName: String,
    val age: Int,
    val living: Boolean
)
""".trimNewLines()
        expect(output).to.equal(expected)
    }

    @Test
    fun givenTypeHasTypeAlias_then_itIsGenerated() {
        val taxi = """
namespace vyne {
    type Person {
        firstName : FirstName as String
        lastName : LastName as String
        age : Age as Int
        living : IsAlive as Boolean
    }
}
        """.trimIndent()
        val output = compileAndGenerate(taxi).trimNewLines()
        val expected = """
package vyne

data class Person(
    val firstName: FirstName,
    val lastName: LastName,
    val age: Age,
    val living: IsAlive
)

typealias vyne.FirstName = kotlin.String

typealias vyne.LastName = kotlin.String

typealias vyne.Age = kotlin.Int

typealias vyne.IsAlive = kotlin.Boolean
""".trimNewLines()
        expect(output).to.equal(expected)
    }

    @Test
    fun generatesArraysAsLists() {
        val taxi = """
namespace vyne {
    type Person {
        firstName : String
        friends : Person[]
    }
}
""".trimIndent()
        val output = compileAndGenerate(taxi).trimNewLines()
        val expected = """
package vyne

import kotlin.String
import kotlin.collections.List

data class Person(val firstName: String, val friends: List<Person>)
        """.trimNewLines()
        expect(output).to.equal(expected)
    }

    @Test
    fun enumTypes() {
        val taxi = """
type Person {
    gender : Gender
}
enum Gender {
    MALE,
    FEMALE
}""".trimIndent()
        val output = compileAndGenerate(taxi).trimNewLines()
        val expected = """
data class Person(val gender: Gender)

package Gender

enum class Gender {
    MALE,

    FEMALE
}
        """.trimNewLines()
        expect(output).to.equal(expected)
    }

    private fun compileAndGenerate(taxi: String): String {
        val taxiDoc = Compiler.forStrings(taxi).compile()
        val output = KotlinGenerator().generate(taxiDoc)
        return output.joinToString("\n") { it.content }
    }
}

fun String.trimNewLines(): String {
    return this.removePrefix("\n").removeSuffix("\n").trim()
}