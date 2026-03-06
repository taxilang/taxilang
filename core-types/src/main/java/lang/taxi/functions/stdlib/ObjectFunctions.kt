package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object ObjectFunctions {
   val functions: List<FunctionApi> = listOf(
      Equals,
      EmptyInstance
   )
}

object Equals : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns `true` if two values are equal. Equality is checked by value and type — values of different semantic types are not equal even if their underlying values match.

      ```taxi
      find {
         match: Boolean = OrderStatus.equals("PENDING")
      }
      ```
      ]]
      declare extension function <A,B> equals(a:A, b:B): Boolean""".trimIndent()
   override val name: QualifiedName = stdLibName("equals")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42Oy04DMQxFf8XyCqR8QSQWwESoRcqMCoUFYREmLi2dcUoeAhTNv5PhobJkZ1_Z556Csd_SaFEicR6hDY7CTbIpRyjQKd0s9JWA865btXeqEbBSS3V5qxqYUOBrpvBRXzc7dlAMA8DWxo7Y7fhZwoX3A1mGszlVFa_tSO7kT4cAgz8lBk9_AWves3_j_wLW-lq39_oLMFsdbKhniUJEWSaBMeWnOj48CqT3A_WJ3DJ6rt7F4NHXoIQUMs3Io8ScbuwQaSbHZPv9wqHkPAy1KPiXivtep0_FXZZoSwEAAA==
      DocsSnippet(
         markdown = "Compares two string values — matching pair returns true, non-matching returns false.",
         query = StubQueryMessage(
            schema = "",
            query = """
               find {
                  same: Boolean = "hello".equals("hello")
                  different: Boolean = "hello".equals("world")
               }
            """.trimIndent(),
            expectedJson = """{"same": true, "different": false}"""
         )
      )
   )
}


object EmptyInstance : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Returns an instance of the requested type with all fields set to their empty state: scalars are `null`, collections are `[]`, and nested objects are recursively emptied.

      ```taxi
      // Create a blank template of a type
      find {
         blank: MyModel = emptyInstance(MyModel)
      }
      ```
      ]]
      declare extension function <A> emptyInstance(instanceType:lang.taxi.Type<A>): A""".trimIndent()
   override val name: QualifiedName = stdLibName("emptyInstance")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/4WOzQrCMBCEX2XZk0LwAQJevOkrGA8x2dBqmrT5QSX03d1W6NXb7LDzzTTMpqNBo0QUOFVKH5auDxaaCgCQ9UASTjF60gGOoLAj76PCA01V-7zbjP36b3vnKFEof0KvmLxdQzMXjzpxT6GUUbZZYC71zvJ6E0jvkUwhe8kx8LSmcJmkUEJJlQSzt8rFdIynBZmLNs-zRRmq99yQ4oM5v3P-AgUTn0L3AAAA
      DocsSnippet(
         markdown = "Creates an empty instance of a model: scalars are null and collections are empty arrays.",
         query = StubQueryMessage(
            schema = """
               type UserTag inherits String
               model User {
                  name: UserName inherits String
                  age: Age inherits Int
                  tags: UserTag[]
               }
            """.trimIndent(),
            query = """
               find {
                  empty: User = emptyInstance(User)
               }
            """.trimIndent(),
            expectedJson = """{"empty": {"name": null, "age": null, "tags": []}}"""
         )
      )
   )
}
