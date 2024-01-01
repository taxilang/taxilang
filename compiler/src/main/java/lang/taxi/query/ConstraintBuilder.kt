package lang.taxi.query

import arrow.core.Either
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.ParameterConstraintContext
import lang.taxi.compiler.ExpressionCompiler
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.Type


class ConstraintBuilder(
   private val expressionCompiler: ExpressionCompiler
) {


   fun build(
      parameterConstraint: ParameterConstraintContext?,
      type: Type
   ): Either<List<CompilationError>, List<Constraint>> {
      if (parameterConstraint == null) {
         return emptyList<Constraint>().right()
      }

      return parameterConstraint.expressionGroup()?.let { buildExpressionConstraint(it, type) }
         ?: emptyList<Constraint>().right()
   }

   private fun buildExpressionConstraint(
      expressionGroup: TaxiParser.ExpressionGroupContext,
      type: Type
   ): Either<List<CompilationError>, List<Constraint>> {
      return expressionCompiler.compile(expressionGroup).map { expression ->
         listOf(ExpressionConstraint(expression))
      }
   }

}

fun TaxiParser.QualifiedNameContext.asDotJoinedPath(): String {
   return this.identifier().joinToString(".") { it.text }
}

