package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object EnumFunctions {
   val functions: List<FunctionApi> = listOf(
      EnumForName,
      HasEnumNamed
      )
}

object EnumForName : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns the enum member matching the provided value string. Throws if no match is found.

      ```taxi
      // Look up an enum member by its string value
      find {
         status: OrderStatus = enumForName(OrderStatus, "PENDING")
      }
      ```
      ]]
      declare extension function <T> enumForName(enumType: lang.taxi.Type<T>, enumName: String): T
   """.trimIndent()
   override val name: QualifiedName = stdLibName("enumForName")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/1WNyw6CMBBFf2UyK036BU1cGKkGF0DQuLEuEMb4gIJ9JJqGf7eIC9zN5N57jkdTXqkpkCMp10CqK9I7W1hnwEMmkihONgyWWZanBxExyMVWrPYigh4ZPh3pd5hebqoCLxUAmO-W_4EWMLDXrU6KhmaThIHEn0PiXKqB2RU6tCxpg9z3DI1153AeTwzp1VFpqdqaVgWrlzjaJPIpaKCEoHzEFXLl6jpAdXsP0_HtP2N80B31AAAA
      DocsSnippet(
         markdown = "Looks up an enum member by its string value and returns it as a typed enum.",
         query = StubQueryMessage(
            schema = """
               enum OrderStatus { PENDING, APPROVED, REJECTED }
            """.trimIndent(),
            query = """
               find {
                  status: OrderStatus = enumForName(OrderStatus, "PENDING")
               }
            """.trimIndent(),
            expectedJson = """{"status": "PENDING"}"""
         )
      )
   )
}

object HasEnumNamed : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if the enum has an explicitly declared member matching the provided name.

      Ignores default entries — an enum with a default value will return `false` for a name that is only matched by the default, not by an explicit declaration.

      ```taxi
      find {
         valid: Boolean = hasEnumNamed(OrderStatus, "PENDING")
      }
      ```
      ]]
      declare extension function <T> hasEnumNamed(enumType: lang.taxi.Type<T>, enumName: String): Boolean
   """.trimIndent()
   override val name: QualifiedName = stdLibName("hasEnumNamed")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/2VPMW7DMAz8CsGpBfwCAR06ukOWtpPlQbUZxa1NKxIN1DD891JyAgTodsc78o4bpu5Ck0ODsgaCz0Txw3kY-EJxkATvEgf2lqe5p7HIsFkGAHYTmTI4Kfq_oBbn1fHqH8SapSjifDL3sKa1vGOF14XiqkXOA_e3EJqCrIcRXg5WcxLHHT3l4fOxGVzUDkIxodn2CpMsXwqbtkL6DdQJ9W9pZr29WSxXLBqNwPxEhryMYwUWtfEjzTUzb9o9x2hw91P3aIqOIc7fevug-x8fg9PXSgEAAA==
      DocsSnippet(
         markdown = "Returns true for a declared enum member name, false for a name not in the enum.",
         query = StubQueryMessage(
            schema = """
               enum OrderStatus { PENDING, APPROVED, REJECTED }
            """.trimIndent(),
            query = """
               find {
                  hasPending: Boolean = hasEnumNamed(OrderStatus, "PENDING")
                  hasUnknown: Boolean = hasEnumNamed(OrderStatus, "UNKNOWN")
               }
            """.trimIndent(),
            expectedJson = """{"hasPending": true, "hasUnknown": false}"""
         )
      )
   )
}
