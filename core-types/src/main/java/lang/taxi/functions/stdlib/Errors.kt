package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object Errors {
   val functions: List<FunctionApi> = listOf(Throw)

}

object Throw : FunctionApi {
   override val taxi: String = """
      [[
      Throws an error, halting execution. Useful for enforcing invariants inline.

      ```taxi
      find { order: Order } as {
         status: OrderStatus = when {
            OrderStatus == 'INVALID' -> throw('Order status is invalid')
            else -> OrderStatus
         }
      }
      ```
      ]]
      declare function throw(error:Any):Nothing""".trimIndent()
   override val name: QualifiedName = stdLibName("throw")
}
