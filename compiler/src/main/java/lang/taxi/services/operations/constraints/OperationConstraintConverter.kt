package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.compiler.ExpressionCompiler

class OperationConstraintConverter(
   private val expressionList: TaxiParser.ExpressionGroupContext?,
   private val returnValueOriginExpressionContext: TaxiParser.OperationReturnValueOriginExpressionContext?,
   private val expressionCompiler: ExpressionCompiler
) {
   fun constraints(): Either<List<CompilationError>, List<Constraint>> {

      val errors = mutableListOf<CompilationError>()
      val constraints = mutableListOf<Constraint>()

      // Compile the returnValueOriginExpressionContext if present
      if (returnValueOriginExpressionContext != null) {
         ReturnValueDerivedFromInputConstraintProvider
            .build(returnValueOriginExpressionContext)
            .getOrElse {
               errors.addAll(it)
               null
            }?.let { constraints.add(it) }
      }

      // Compile the expression list, if present
      if (expressionList != null) {
         ExpressionConstraintBuilder.build(expressionList, expressionCompiler)
            .getOrElse {
               errors.addAll(it)
               null
            }
            ?.let { constraints.addAll(it) }
      }

      return if (errors.isNotEmpty()) {
         errors.left()
      } else {
         constraints.right()
      }
   }
}
