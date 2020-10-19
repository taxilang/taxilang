package lang.taxi.functions

import lang.taxi.types.Accessor
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type


class FunctionAccessor(val function: Function, val inputs:List<Accessor>) : Accessor, TaxiStatementGenerator {
   override val returnType: Type
      get() = function.returnType ?: PrimitiveType.ANY
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
