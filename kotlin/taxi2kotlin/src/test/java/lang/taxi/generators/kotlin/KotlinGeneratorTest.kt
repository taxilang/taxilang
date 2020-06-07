package lang.taxi.generators.kotlin

import com.nhaarman.mockitokotlin2.mock
import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import lang.taxi.Compiler
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.packages.TaxiPackageProject
import org.junit.Test
import java.nio.file.Path

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

open class Person(
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
      val expected = """package vyne

open class Person(
  val firstName: FirstName,
  val lastName: LastName,
  val age: Age,
  val living: IsAlive
)

package vyne

import kotlin.String

typealias FirstName = String

package vyne

import kotlin.String

typealias LastName = String

package vyne

import kotlin.Int

typealias Age = Int

package vyne

import kotlin.Boolean

typealias IsAlive = Boolean
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

open class Person(
  val firstName: String,
  val friends: List<Person>
)""".trimNewLines()
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
      val expected = """open class Person(
  val gender: Gender
)

enum class Gender {
  MALE,

  FEMALE
}""".trimNewLines()
      expect(output).to.equal(expected)
   }

   @Test
   fun enumTypesThatInherit() {
      val taxi = """
enum Direction { Buy, Sell }
// Note - when we fix enum generation, this should stop compiling
type BankDirection inherits Direction
"""
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """enum class Direction {
  Buy,

  Sell
}

typealias BankDirection = Direction""".trimNewLines()

      output.should.equal(expected)
   }

   @Test
   fun scalarTypes() {
      val taxi = """
         type Name inherits String
         type FirstName inherits Name
         type alias GivenName as FirstName
      """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
         import kotlin.String

         typealias Name = String

         typealias FirstName = Name

         typealias GivenName = FirstName
      """.trimIndent().trimNewLines()

      output.should.equal(expected)
   }

   @Test
   fun emptyTypesShouldBeInterface() {
      val taxi = """
         type Person
      """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
         interface Person
      """.trimIndent().trimNewLines()

      output.should.equal(expected)
   }

   @Test
   fun objectTypesThatInheritEmptyTypes() {
      val taxi = """
type Instrument
type Money inherits Instrument {
            currency : CurrencySymbol as String
            amount : MoneyAmount as Decimal
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """interface Instrument

open class Money(
  val currency: CurrencySymbol,
  val amount: MoneyAmount
) : Instrument

import kotlin.String

typealias CurrencySymbol = String

import java.math.BigDecimal

typealias MoneyAmount = BigDecimal
      """.trimIndent().trimNewLines()
      output.should.equal(expected)
   }
   @Test
   fun objectTypesThatInherit() {
      val taxi = """
         type Money {
            currency : CurrencySymbol as String
            amount : MoneyAmount as Decimal
         }
         type Notional inherits Money
      """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """open class Money(
  val currency: CurrencySymbol,
  val amount: MoneyAmount
)

open class Notional(
  currency: CurrencySymbol,
  amount: MoneyAmount
) : Money(currency = currency, amount = amount)

import kotlin.String

typealias CurrencySymbol = String

import java.math.BigDecimal

typealias MoneyAmount = BigDecimal
      """.trimIndent().trimNewLines()

      output.should.equal(expected)
   }



   private fun compileAndGenerate(taxi: String): String {
      val taxiDoc = Compiler.forStrings(taxi).compile()
      val output = KotlinGenerator().generate(taxiDoc, emptyList(),MockEnvironment)
      return output.joinToString("\n") { it.content }
   }
}

fun String.trimNewLines(): String {
   return this.removePrefix("\n").removeSuffix("\n").trim()
}

object MockEnvironment : TaxiEnvironment {
   override val projectRoot: Path
      get() = TODO("Not yet implemented")
   override val outputPath: Path
      get() = TODO("Not yet implemented")
   override val project: TaxiPackageProject
      get() = TODO("Not yet implemented")

}
