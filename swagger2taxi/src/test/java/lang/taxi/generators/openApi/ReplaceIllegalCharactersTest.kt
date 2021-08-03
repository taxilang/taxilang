package lang.taxi.generators.openApi

import com.winterbe.expekt.should
import lang.taxi.generators.openApi.Utils.replaceIllegalCharacters
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

internal class ReplaceIllegalCharactersTest {

   @TestFactory
   fun `replaceIllegalCharacters replaces characters as expected`() = listOf(
      "" to "",
      " " to "_",
      "ab" to "ab",
      "a_b" to "a_b",
      "a-b-c" to "a_b_c",
      "a b c" to "a_b_c",
      "a - b - c" to "a_b_c",
      "a  b" to "a_b",
      "a | b" to "a_b",
      "a | b" to "a_b",
      "\$anIdentifier\$909" to "\$anIdentifier\$909",
      "123" to "123",
   ).map { (original, expected) ->
      dynamicTest("\"$original\".replaceIllegalCharacters() == \"$expected\"") {
         original.replaceIllegalCharacters().should.equal(expected)
      }
   }
}
