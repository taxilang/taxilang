package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object Parsers {
   val functions: List<FunctionApi> = listOf(
      ParseJson
   )
}


object ParseJson : FunctionApi, HasRunnableExamples {
   override val taxi = """
      [[
      Parses a JSON string into the specified type.

      This is useful when a field contains a JSON payload encoded as a string (e.g., from a message queue or API response),
      and you need to deserialize it into a structured type.

      Computed fields and functions on the target type are evaluated after parsing.

      ```taxi
      model Event {
         name : String
         payload : String
         parsed : Payload = parseJson(this.payload, Payload)
      }
      ```
      ]]
      declare extension function <T> parseJson(source: String, type: Type<T>):T""".trimIndent()
   override val name: QualifiedName = stdLibName("parseJson")

   override val examples: List<DocsSnippet> = listOf(
      DocsSnippet(
         markdown = """This example demonstrates parsing a JSON string embedded in a field into a structured type.""",
         query = StubQueryMessage(
            schema = """
               model Address {
                  city: String
                  country: String
               }
               model Customer {
                  name: String
                  addressJson: String
                  address: Address = parseJson(this.addressJson, Address)
               }
            """.trimIndent(),
            query = """
               given {
                  customer: Customer = {
                     name: 'Alice',
                     addressJson: '{ "city": "London", "country": "UK" }'
                  }
               }
               find {
                  name: customer.name
                  address: customer.address
               }
            """.trimIndent(),
            // Cannot assert yet, as this function is not deployed -- this can be uncommented now if you're reading this,
            // but need to wait for the new function deployed on playground
//            expectedJson = """{
//               "name": "Alice",
//               "address": {
//                  "city": "London",
//                  "country": "UK"
//               }
//            }"""
         )
      ),
      DocsSnippet(
         markdown = """This example shows that computed fields on the target type are evaluated after parsing.""",
         query = StubQueryMessage(
            schema = """
               model Payload {
                  message: String
                  upperMessage: String = this.message.upperCase()
               }
               model Event {
                  name: String
                  payload: String
                  parsed: Payload = parseJson(this.payload, Payload)
               }
            """.trimIndent(),
            query = """
               given {
                  event: Event = {
                     name: 'greeting',
                     payload: '{ "message": "hello world" }'
                  }
               }
               find {
                  name: event.name
                  parsed: event.parsed
               }
            """.trimIndent(),
            // Cannot assert yet, as this function is not deployed -- this can be uncommented now if you're reading this,
            // but need to wait for the new function deployed on playground

//            expectedJson = """{
//               "name": "greeting",
//               "parsed": {
//                  "message": "hello world",
//                  "upperMessage": "HELLO WORLD"
//               }
//            }"""
         )
      )
   )
}
