package lang.taxi.functions

import lang.taxi.types.Accessor
import lang.taxi.types.TaxiStatementGenerator


class FunctionAccessor(val function: Function, val inputs:List<Accessor>) : Accessor, TaxiStatementGenerator {
   override fun asTaxi(): String {
      val parametersAsTaxi = inputs.joinToString(",") { inputAccessor ->
         when (inputAccessor) {
            is TaxiStatementGenerator -> inputAccessor.asTaxi().removePrefix("by").trim()
            else -> "/* Within FunctionAccessor, input of type ${inputAccessor::class.simpleName} does not support taxi generation */"
         }
      }
      return "by ${function.qualifiedName}( $parametersAsTaxi )"
   }

}
