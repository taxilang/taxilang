package lang.taxi.generators.kotlin

import com.winterbe.expekt.expect
import org.junit.Test

class EnumGeneratorTest {

   @Test
   fun generatesValuesInsideEnums() {
      val src = """
         enum Country {
            NEW_ZEALAND('NZ'),
            AUSTRALIA('AUS')
         }

         enum CountryCode {
            NEW_ZEALAND(64),
            AUSTRALIA(61)
         }

         enum City {
            AUCKLAND,
            SYDNEY
         }
      """.trimIndent()
      val output = compileAndGenerate(src).trimNewLines()

      val expected = """import kotlin.String
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.Country

@DataType(
  value = Country,
  imported = true
)
enum class Country(
  val value: String
) {
  NEW_ZEALAND("NZ"),

  AUSTRALIA("AUS");
}

import kotlin.Int
import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.CountryCode

@DataType(
  value = CountryCode,
  imported = true
)
enum class CountryCode(
  val value: Int
) {
  NEW_ZEALAND(64),

  AUSTRALIA(61);
}

import lang.taxi.annotations.DataType
import taxi.generated.TypeNames.City

@DataType(
  value = City,
  imported = true
)
enum class City {
  AUCKLAND,

  SYDNEY
}

package taxi.generated

import kotlin.String

object TypeNames {
  const val Country: String = "Country"

  const val CountryCode: String = "CountryCode"

  const val City: String = "City"
}
""".trimNewLines()
      expect(output).to.equal(expected)
   }
}
