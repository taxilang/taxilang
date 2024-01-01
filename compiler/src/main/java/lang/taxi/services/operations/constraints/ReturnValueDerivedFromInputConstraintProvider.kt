package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.NamespaceQualifiedTypeResolver
import lang.taxi.TaxiParser
import lang.taxi.toAttributePath
import lang.taxi.toCompilationUnits
import lang.taxi.types.Type

object ReturnValueDerivedFromInputConstraintProvider  {
   fun build(constraint: TaxiParser.OperationReturnValueOriginExpressionContext): Either<List<CompilationError>, Constraint> {
      return ReturnValueDerivedFromParameterConstraint(constraint.qualifiedName().toAttributePath(), constraint.toCompilationUnits()).right()
   }

}
