package lang.taxi.generators.openApi.v3

import com.winterbe.expekt.should
import io.swagger.oas.models.media.Schema
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

internal class TaxiExtensionTest {

   @TestFactory
   fun `taxiExtension returns null if OpenAPI has no extension`(): List<DynamicTest> =
      listOf(
         emptyMap<String, Any>(),
         null,
         mapOf("whatever" to 23),
      ).map { input ->
         dynamicTest("extensions $input means taxiExtension is null") {
            val schema = Schema<Any>().apply {
               extensions = input
            }

            schema.taxiExtension.should.be.`null`
         }
      }

   @TestFactory
   fun `taxiExtension returns a TaxiExtension from an OpenAPI Schema`(): List<DynamicTest> =
      listOf(
         mapOf("name" to "foo.Bar", "create" to true ) to TaxiExtension(name = "foo.Bar", create = true ),
         mapOf("name" to "foo.Bar", "create" to false) to TaxiExtension(name = "foo.Bar", create = false),
         mapOf("name" to "foo.Bar", "create" to null ) to TaxiExtension(name = "foo.Bar", create = null ),
         mapOf("name" to "foo.Bar"                   ) to TaxiExtension(name = "foo.Bar", create = null ),
         mapOf("name" to "foo.Bar", "ignored" to 42  ) to TaxiExtension(name = "foo.Bar", create = null ),
      ).map { (input, expected) ->
         dynamicTest("x-taxi-type object $input is parsed to $expected") {
            val schema = Schema<Any>().apply {
               extensions = mapOf("x-taxi-type" to input)
            }

            schema.taxiExtension.should.equal(expected)
         }
      }

   @TestFactory
   fun `taxiExtension reports a useful error when failing to parse a TaxiExtension from an OpenAPI Schema`(): List<DynamicTest> =
      listOf(
         mapOf("create" to true                          ) to """Instantiation of [simple type, class lang.taxi.generators.openApi.v3.TaxiExtension] value failed for JSON property name due to missing (therefore NULL) value for creator parameter name which is a non-nullable type""",
         mapOf("name"   to "foo.Bar", "create" to "false") to """Cannot coerce String value ("false") to `java.lang.Boolean` value""",
      ).map { (input, expected) ->
         dynamicTest("x-taxi-type object $input is parsed to $expected") {
            val schema = Schema<Any>().apply {
               extensions = mapOf("x-taxi-type" to input)
            }

            val exception = assertThrows<IllegalArgumentException> {
               schema.taxiExtension
            }
            exception.message.should.startWith(expected)
         }
      }
}
