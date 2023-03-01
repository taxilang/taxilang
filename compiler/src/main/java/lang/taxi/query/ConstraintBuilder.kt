package lang.taxi.query

import arrow.core.*
import lang.taxi.CompilationError
import lang.taxi.NamespaceQualifiedTypeResolver
import lang.taxi.TaxiParser
import lang.taxi.TaxiParser.ParameterConstraintContext
import lang.taxi.compiler.ExpressionCompiler
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.services.operations.constraints.PropertyToParameterConstraintProvider
import lang.taxi.types.Type
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList


class ConstraintBuilder(
   private val typeResolver: NamespaceQualifiedTypeResolver,
   private val expressionCompiler: ExpressionCompiler
) {
   private val propertyToParameterConstraintProvider = PropertyToParameterConstraintProvider()


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

   fun build(
      parameterConstraintExpressionList: TaxiParser.ParameterConstraintExpressionListContext,
      type: Type
   ): Either<List<CompilationError>, List<Constraint>> {
      val constraints: Either<List<CompilationError>, List<Constraint>> =
         parameterConstraintExpressionList.parameterConstraintExpression().map { constraintExpression ->
            propertyToParameterConstraintProvider.build(type, typeResolver, constraintExpression)
         }.invertEitherList().flattenErrors()
      return constraints
   }
}

fun TaxiParser.QualifiedNameContext.asDotJoinedPath(): String {
   return this.identifier().joinToString(".") { it.text }
}

