package lang.taxi.functions

import lang.taxi.types.Accessor
import lang.taxi.types.FormulaOperator
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

class FunctionExpressionAccessor(val functionAccessor: FunctionAccessor, val operator: FormulaOperator, val operand: Any) : Accessor, TaxiStatementGenerator {
   override fun asTaxi(): String {
      val parametersAsTaxi = functionAccessor.inputs.joinToString(",") { inputAccessor ->
         when (inputAccessor) {
            is TaxiStatementGenerator -> inputAccessor.asTaxi().removePrefix("by").trim()
            else -> "/* Within FunctionAccessor, input of type ${inputAccessor::class.simpleName} does not support taxi generation */"
         }
      }
      return "by ${functionAccessor.function.qualifiedName}( $parametersAsTaxi ${operator.symbol} $operand)"
   }

}
