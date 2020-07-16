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

      val expected = """import java.lang.String
import lang.taxi.annotations.DataType

@DataType(TypeNames.Country)
enum class Country(
  value: String
) {
  NEW_ZEALAND("NZ"),

  AUSTRALIA("AUS")
}

import java.lang.Integer
import lang.taxi.annotations.DataType

@DataType(TypeNames.CountryCode)
enum class CountryCode(
  value: Integer
) {
  NEW_ZEALAND(64),

  AUSTRALIA(61)
}

import lang.taxi.annotations.DataType

@DataType(TypeNames.City)
enum class City {
  AUCKLAND,

  SYDNEY
}

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
