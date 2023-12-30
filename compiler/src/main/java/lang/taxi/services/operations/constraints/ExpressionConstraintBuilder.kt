package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.TaxiParser.ExpressionGroupContext
import lang.taxi.compiler.ExpressionCompiler

// Migrated code here to allow reuse from multiple places.
object ExpressionConstraintBuilder {
   fun build(
      constraintList: ExpressionGroupContext?,
      expressionCompiler: ExpressionCompiler
   ): Either<List<CompilationError>, List<Constraint>> {
      if (constraintList == null) {
         return emptyList<Constraint>().right()
      }
      return expressionCompiler.compile(constraintList).map { expression ->
         listOf(ExpressionConstraint(expression))
      }
   }
}
