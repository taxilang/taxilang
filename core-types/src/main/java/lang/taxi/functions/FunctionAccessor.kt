package lang.taxi.functions

import lang.taxi.accessors.Accessor
import lang.taxi.types.FormulaOperator
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type


data class FunctionAccessor private constructor(
   val function: Function,
   val inputs: List<Accessor>,
   private val rawFunction: Function = function
) : Accessor, TaxiStatementGenerator {
   val qualifiedName = function.qualifiedName
   val parameters = function.parameters
   companion object {
      /**
       * Constructs a FunctionAccessor where any typeArguments present in the function contract
       * are resolved using the provided inputs
       */
      fun buildAndResolveTypeArguments(function: Function, inputs: List<Accessor>): FunctionAccessor {
         val typeArgResolvedDefinition: FunctionDefinition = function.resolveTypeParametersFromInputs(inputs)
         val typeArgResolvedFunction = function.copy(definition = typeArgResolvedDefinition)
         return FunctionAccessor(typeArgResolvedFunction, inputs, function)
      }
   }

   override val returnType: Type = function.returnType ?: PrimitiveType.ANY
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

class FunctionExpressionAccessor(
   val functionAccessor: FunctionAccessor,
   val operator: FormulaOperator,
   val operand: Any
) : Accessor, TaxiStatementGenerator {
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
