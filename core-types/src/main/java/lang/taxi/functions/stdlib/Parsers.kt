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
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/42Qu04DMRBFf8WaZkGy8gGWUkRUPDpEhSnMekgMu+NgexGR5X/Hj92wkSjoPHce91xH8P0BRwUCRqtxYDutHXrPoiTGehNOgj0GZ2hfaztRcCspSWprN5MPdkTX9kiNuN5T7eidt/SHLM6mW3ZUzmOZuwoH4zerRb5MXWdb4PA5oTtl7L35QppxZwjxi7NtnQWp2w2mx4437QKri0xCCSxB5NeDJW1JAi9qi90aT/cSWOrKhVQ/4M2QXsdeKDalvIh57sxKC5Ij58GAzoOIiYMP02t+Pr9wwO8j9gF1IcxRo4R6s3LUJI1vOZf1+O8MqXj7oPqPWw2CpmHIKM6+Z8NWph9RZDBLHAIAAA==
      DocsSnippet(
         markdown = "Parses a JSON string embedded in a model field into a structured type.",
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
            expectedJson = """{"name": "Alice", "address": {"city": "London", "country": "UK"}}"""
         )
      )
   )
}
