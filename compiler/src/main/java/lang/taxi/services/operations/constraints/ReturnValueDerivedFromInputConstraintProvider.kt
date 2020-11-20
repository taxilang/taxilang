package lang.taxi.services.operations.constraints

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.NamespaceQualifiedTypeResolver
import lang.taxi.TaxiParser
import lang.taxi.toAttributePath
import lang.taxi.toCompilationUnits
import lang.taxi.types.Type

class ReturnValueDerivedFromInputConstraintProvider : ConstraintProvider {
   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      return constraint.operationReturnValueOriginExpression() != null
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<List<CompilationError>, Constraint> {
      return Either.right(ReturnValueDerivedFromParameterConstraint(constraint.operationReturnValueOriginExpression().qualifiedName().toAttributePath(), constraint.toCompilationUnits()))
   }

}
