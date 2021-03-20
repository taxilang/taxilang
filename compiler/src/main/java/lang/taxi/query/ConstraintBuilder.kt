package lang.taxi.query

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.NamespaceQualifiedTypeResolver
import lang.taxi.TaxiParser
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.PropertyToParameterConstraintProvider
import lang.taxi.types.Type
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList


class ConstraintBuilder(private val typeResolver: NamespaceQualifiedTypeResolver) {
   private val propertyToParameterConstraintProvider = PropertyToParameterConstraintProvider()

   fun build(parameterConstraintExpressionList: TaxiParser.ParameterConstraintExpressionListContext,
             type: Type
   ): Either<List<CompilationError>, List<Constraint>> {
      val constraints: Either<List<CompilationError>, List<Constraint>> = parameterConstraintExpressionList.parameterConstraintExpression().map { constraintExpression ->
         propertyToParameterConstraintProvider.build(type, typeResolver, constraintExpression)
      }.invertEitherList().flattenErrors()
      return constraints
   }
}

fun TaxiParser.QualifiedNameContext.asDotJoinedPath(): String {
   return this.Identifier().joinToString(".") { it.text }
}

