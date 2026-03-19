package lang.taxi.functions.stdlib

import lang.taxi.docs.StubQueryMessage
import lang.taxi.types.QualifiedName

object Transformations {
   val functions: List<FunctionApi> = listOf(Convert,ToRawType)
}

object Convert : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
      [[
      Converts a source value to the target type using only the data already available — no service calls are made.

      This is faster than a full projection (`source as TargetType`), but less powerful: only the fields present in the source are mapped. Missing fields are not discovered from services.

      ```taxi
      // Convert an Order to a Receipt using only the Order's own fields
      find {
         receipt: Receipt = Order.convert(Receipt)
      }
      ```
      ]]
      declare extension function <T> convert(source: Any, targetType: lang.taxi.Type<T>): T""".trimIndent()
   override val name: QualifiedName = stdLibName("convert")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22OP0_DMBDFv8rplhbJqtQxljogsZQlEow1g7GP1pCcg-1UoCjfHSe2ygDT_X2_9yaM5kK9Rom9t9RBGywFmBQDgLOyzEcLji8UXIrwnILj83rXvR85Sbhf6-_LAxnX607xrLhQn8iQG9Jf7j-cLEOBnyOF75zq7K7EVecXTZXCAaYVtGn3G3FDNM2uaWBerd8c26oMxV_eghwKZWc8Xymkbd3fFfNBB91TohBRTrPAmMbX3J5eBNLXQCaRfYyec7xJYWUrlNkMnV0ahe1eoci1BFt2a7R5wcekzcfRouSx67Jb8O-ZWcb5B5sEy0WSAQAA
      DocsSnippet(
         markdown = "Converts an Order to a Receipt model using only the fields present in the source, without calling any services.",
         query = StubQueryMessage(
            schema = """
               model Order {
                  id: OrderId inherits String
                  amount: Amount inherits Decimal
               }
               model Receipt {
                  id: OrderId
                  amount: Amount
               }
            """.trimIndent(),
            query = """
               given {
                  order: Order = { id: 'O1', amount: 99.99 }
               }
               find {
                  receipt: Receipt = Order.convert(Receipt)
               }
            """.trimIndent(),
            expectedJson = """{"receipt": {"id": "O1", "amount": 99.99}}"""
         )
      )
   )
}

object ToRawType : FunctionApi, HasRunnableExamples {
   override val taxi: String = """
   [[
   Removes the semantic typing from a scalar value, returning it typed as its raw primitive.

   Useful when you need to compare values by their raw content while ignoring their semantic type.

   - For a scalar value: strips the semantic type, returning the underlying primitive.
   - For an array of scalars: strips the semantic type from each element.

   Not supported on objects or arrays of objects.

   ```taxi
   find { order: Order } as {
      rawId: String = order.id.toRawType()
   }
   ```
   ]]
   declare extension function toRawType(source: Any):Any
   """.trimIndent()
   override val name: QualifiedName = stdLibName("toRawType")

   override val examples: List<DocsSnippet> = listOf(
      // playgroundUrl: http://192.168.5.236:9500/?enableDevTools=true#pako:H4sIAAAAAAAA/22OOw/CMAyE/0rkBZAKgjUSGwsslXhMhCE0BsLDLU5Kqar+dwxFTGy27u67ayBkJ7xZ0BDrAlXKDnnulKcTso9BrSJ7Ohr6qJvwT4QE7iVyLYyjfyCpxpBSeUfSP+RU9dLlbDgeT3qGWkMHT66zsq3exo4mvm90FPOlrdZS3B9IQmoKy/aGETmAbtoEQiz3cm53CeCzwCyiW4ScZEhj4EM1oJWBb6+BNyREm13mDjSV16swOT9LsnvbFwfpNoIQAQAA
      DocsSnippet(
         markdown = "Strips the semantic type from a typed scalar, returning the raw primitive value.",
         query = StubQueryMessage(
            schema = """
               type OrderId inherits String
            """.trimIndent(),
            query = """
               given {
                  orderId: OrderId = 'ORD-001'
               }
               find {
                  rawId: String = orderId.toRawType()
               }
            """.trimIndent(),
            expectedJson = """{"rawId": "ORD-001"}"""
         )
      )
   )
}
