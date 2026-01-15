package lang.taxi.generators.kotlin

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import io.kotest.matchers.shouldBe
import lang.taxi.Compiler
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.TaxiPackageProject
import org.junit.jupiter.api.Test
import java.nio.file.Path


class KotlinGeneratorTest {

   @Test
   fun canGenerateNestedObject() {
      val baz = TypeSpec.objectBuilder("Baz")
         .addProperty(PropertySpec.builder("buzz", String::class)
            .initializer("%S", "buzzzzz")
            .build())
         .build()

      val top = TypeSpec.objectBuilder("TypeName")
         .addType(baz)
         .build()

      val b = top.toString()
      val file = FileSpec.builder("", "QuickTest")
         .addType(top)
         .build()
//      file.
   }
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
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Person

@DataType(
  value = Person,
  imported = true
)
open class Person(
  val firstName: String,
  val lastName: String,
  val age: Int,
  val living: Boolean
)

package taxi.generated

import kotlin.String

object TypeNames {
  const val Person: String = "Person"
}
""".trimNewLines()
      output.shouldBe(expected)
   }

   @Test
   fun givenTypeHasTypeAlias_then_itIsGenerated() {
      val taxi = """
namespace foo {
    type Person {
        firstName : FirstName inherits String
        lastName : LastName inherits String
        age : Age inherits Int
        living : IsAlive inherits Boolean
    }
}
        """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
package foo

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.foo.Person

@DataType(
  value = Person,
  imported = true
)
open class Person(
  val firstName: FirstName,
  val lastName: LastName,
  val age: Age,
  val living: IsAlive
)

package foo

import kotlin.String
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.foo.FirstName

@DataType(
  value = FirstName,
  imported = true
)
typealias FirstName = String

package foo

import kotlin.String
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.foo.LastName

@DataType(
  value = LastName,
  imported = true
)
typealias LastName = String

package foo

import kotlin.Int
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.foo.Age

@DataType(
  value = Age,
  imported = true
)
typealias Age = Int

package foo

import kotlin.Boolean
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.foo.IsAlive

@DataType(
  value = IsAlive,
  imported = true
)
typealias IsAlive = Boolean

package taxi.generated

import kotlin.String

object TypeNames {
  object foo {
    const val Person: String = "foo.Person"

    const val FirstName: String = "foo.FirstName"

    const val LastName: String = "foo.LastName"

    const val Age: String = "foo.Age"

    const val IsAlive: String = "foo.IsAlive"
  }
}
""".trimNewLines()
      output.shouldBe(expected)
   }

   @Test
   fun generatesArraysAsLists() {
      val taxi = """
namespace com.foo {
    type Person {
        firstName : String
        friends : Person[]
    }
}
""".trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
package com.foo

import kotlin.String
import kotlin.collections.List
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.com.foo.Person

@DataType(
  value = Person,
  imported = true
)
open class Person(
  val firstName: String,
  val friends: List<Person>
)

package taxi.generated

import kotlin.String

object TypeNames {
  object com {
    object foo {
      const val Person: String = "com.foo.Person"
    }
  }
}""".trimNewLines()
      output.shouldBe(expected)
   }

   @Test
   fun nullableTypesAreGeneratedCorrectly() {
      val taxi = """
      type MiddleName inherits String
      type Person {
         middleName : MiddleName?
      }
      """.trimMargin()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """import kotlin.String
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.MiddleName

@DataType(
  value = MiddleName,
  imported = true
)
typealias MiddleName = String

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Person

@DataType(
  value = Person,
  imported = true
)
open class Person(
  val middleName: MiddleName?
)

package taxi.generated

import kotlin.String

object TypeNames {
  const val MiddleName: String = "MiddleName"

  const val Person: String = "Person"
}"""
      output.should.equal(expected)
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
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Person

@DataType(
  value = Person,
  imported = true
)
open class Person(
  val gender: Gender
)

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Gender

@DataType(
  value = Gender,
  imported = true
)
enum class Gender {
  MALE,

  FEMALE
}

package taxi.generated

import kotlin.String

object TypeNames {
  const val Person: String = "Person"

  const val Gender: String = "Gender"
}
""".trimNewLines()
      output.shouldBe(expected)
   }

   @Test
   fun enumTypesThatInherit() {
      val taxi = """
enum Direction { Buy, Sell }
// Note - when we fix enum generation, this should stop compiling
enum BankDirection inherits Direction
"""
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Direction

@DataType(
  value = Direction,
  imported = true
)
enum class Direction {
  Buy,

  Sell
}

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.BankDirection

@DataType(
  value = BankDirection,
  imported = true
)
typealias BankDirection = Direction

package taxi.generated

import kotlin.String

object TypeNames {
  const val Direction: String = "Direction"

  const val BankDirection: String = "BankDirection"
}
""".trimNewLines()

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
      val expected = """import kotlin.String
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Name

@DataType(
  value = Name,
  imported = true
)
typealias Name = String

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.FirstName

@DataType(
  value = FirstName,
  imported = true
)
typealias FirstName = Name

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.GivenName

@DataType(
  value = GivenName,
  imported = true
)
typealias GivenName = FirstName

package taxi.generated

import kotlin.String

object TypeNames {
  const val Name: String = "Name"

  const val FirstName: String = "FirstName"

  const val GivenName: String = "GivenName"
}""".trimIndent().trimNewLines()

      output.should.equal(expected)
   }

   @Test
   fun emptyTypesShouldBeInterface() {
      val taxi = """
         type Person
      """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Person

@DataType(
  value = Person,
  imported = true
)
interface Person

package taxi.generated

import kotlin.String

object TypeNames {
  const val Person: String = "Person"
}
      """.trimIndent().trimNewLines()

      output.should.equal(expected)
   }

   @Test
   fun objectTypesThatInheritEmptyTypes() {
      val taxi = """
type Instrument
type Money inherits Instrument {
            currency : CurrencySymbol inherits String
            amount : MoneyAmount inherits Decimal
         }
      """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Instrument

@DataType(
  value = Instrument,
  imported = true
)
interface Instrument

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Money

@DataType(
  value = Money,
  imported = true
)
open class Money(
  val currency: CurrencySymbol,
  val amount: MoneyAmount
) : Instrument

import kotlin.String
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.CurrencySymbol

@DataType(
  value = CurrencySymbol,
  imported = true
)
typealias CurrencySymbol = String

import java.math.BigDecimal
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.MoneyAmount

@DataType(
  value = MoneyAmount,
  imported = true
)
typealias MoneyAmount = BigDecimal

package taxi.generated

import kotlin.String

object TypeNames {
  const val Instrument: String = "Instrument"

  const val Money: String = "Money"

  const val CurrencySymbol: String = "CurrencySymbol"

  const val MoneyAmount: String = "MoneyAmount"
}
      """.trimIndent().trimNewLines()
      output.should.equal(expected)
   }
   @Test
   fun objectTypesThatInherit() {
      val taxi = """
         type Money {
            currency : CurrencySymbol inherits String
            amount : MoneyAmount inherits Decimal
         }
         type Notional inherits Money
      """.trimIndent()
      val output = compileAndGenerate(taxi).trimNewLines()
      val expected = """
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Money

@DataType(
  value = Money,
  imported = true
)
open class Money(
  val currency: CurrencySymbol,
  val amount: MoneyAmount
)

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Notional

@DataType(
  value = Notional,
  imported = true
)
open class Notional(
  currency: CurrencySymbol,
  amount: MoneyAmount
) : Money(currency = currency, amount = amount)

import kotlin.String
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.CurrencySymbol

@DataType(
  value = CurrencySymbol,
  imported = true
)
typealias CurrencySymbol = String

import java.math.BigDecimal
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.MoneyAmount

@DataType(
  value = MoneyAmount,
  imported = true
)
typealias MoneyAmount = BigDecimal

package taxi.generated

import kotlin.String

object TypeNames {
  const val Money: String = "Money"

  const val Notional: String = "Notional"

  const val CurrencySymbol: String = "CurrencySymbol"

  const val MoneyAmount: String = "MoneyAmount"
}
      """.trimIndent().trimNewLines()

      output.should.equal(expected)
   }

   @Test
   fun `filters types by namespace`() {
      val taxi = """
         namespace com.foo {
            type Person {
               name : String
            }
         }
         namespace com.bar {
            type Company {
               name : String
            }
         }
      """.trimIndent()
      val taxiDoc = Compiler.forStrings(taxi).compile()
      val output = KotlinGenerator(typesOrNamespaces = listOf("com.foo")).generate(taxiDoc, emptyList(), MockEnvironment)
      val generatedContent = output.joinToString("\n") { it.content }

      generatedContent.should.contain("class Person")
      generatedContent.should.not.contain("class Company")
   }

   @Test
   fun `filters specific type by fully qualified name`() {
      val taxi = """
         namespace com.foo {
            type Person {
               name : String
            }
            type Company {
               name : String
            }
         }
      """.trimIndent()
      val taxiDoc = Compiler.forStrings(taxi).compile()
      val output = KotlinGenerator(typesOrNamespaces = listOf("com.foo.Person")).generate(taxiDoc, emptyList(), MockEnvironment)
      val generatedContent = output.joinToString("\n") { it.content }

      generatedContent.should.contain("class Person")
      generatedContent.should.not.contain("class Company")
   }

   @Test
   fun `generates all types when filter list is empty`() {
      val taxi = """
         namespace com.foo {
            type Person {
               name : String
            }
         }
         namespace com.bar {
            type Company {
               name : String
            }
         }
      """.trimIndent()
      val taxiDoc = Compiler.forStrings(taxi).compile()
      val output = KotlinGenerator(typesOrNamespaces = emptyList()).generate(taxiDoc, emptyList(), MockEnvironment)
      val generatedContent = output.joinToString("\n") { it.content }

      generatedContent.should.contain("class Person")
      generatedContent.should.contain("class Company")
   }

   @Test
   fun `filters types with multiple namespaces`() {
      val taxi = """
         namespace com.foo {
            type Person {
               name : String
            }
         }
         namespace com.bar {
            type Company {
               name : String
            }
         }
         namespace com.baz {
            type Product {
               name : String
            }
         }
      """.trimIndent()
      val taxiDoc = Compiler.forStrings(taxi).compile()
      val output = KotlinGenerator(typesOrNamespaces = listOf("com.foo", "com.bar")).generate(taxiDoc, emptyList(), MockEnvironment)
      val generatedContent = output.joinToString("\n") { it.content }

      generatedContent.should.contain("class Person")
      generatedContent.should.contain("class Company")
      generatedContent.should.not.contain("class Product")
   }


}

fun compileAndGenerate(taxi: String): String {
   val taxiDoc = Compiler.forStrings(taxi).compile()
   val output = KotlinGenerator().generate(taxiDoc, emptyList(),MockEnvironment)
   return output.joinToString("\n") { it.content }
}
fun String.trimNewLines(): String {
   return this.removePrefix("\n").removeSuffix("\n").trim()
}

object MockEnvironment : TaxiProjectEnvironment {
   override val projectRoot: Path
      get() = TODO("Not yet implemented")
   override val outputPath: Path
      get() = TODO("Not yet implemented")
   override val project: TaxiPackageProject
      get() = TODO("Not yet implemented")

}
