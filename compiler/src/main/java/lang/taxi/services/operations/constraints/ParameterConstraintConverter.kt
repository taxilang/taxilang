package lang.taxi.services.operations.constraints

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.NamespaceQualifiedTypeResolver
import lang.taxi.TaxiParser
import lang.taxi.TypeSystem
import lang.taxi.services.*
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.utils.invertEitherList

interface ConstraintProvider {
   fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean
   fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint>
}

interface ValidatingConstraintProvider : ConstraintProvider {
   fun applies(constraint: Constraint): Boolean
   fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget)
}

object ConstraintProviders {
   val providers = listOf(
      PropertyToParameterConstraintProvider(),
      ReturnValueDerivedFromInputConstraintProvider()
   )
}

class ConstraintValidator(providers: List<ConstraintProvider> = ConstraintProviders.providers) {
   private val validatingProviders = providers.filterIsInstance<ValidatingConstraintProvider>()
   fun validateAll(typeSystem: TypeSystem, services: List<Service>) {
      typeSystem.typeList().filterIsInstance<ObjectType>()
         .forEach { type ->
            type.allFields.forEach { field ->
               field.constraints.forEach { constraint ->
                  validate(constraint, typeSystem, field)
               }
            }
         }

      services.flatMap { it.operations }
         .forEach { operation ->
            operation.contract?.let { contract ->
               contract.returnTypeConstraints.forEach {
                  validate(it, typeSystem, contract)
               }
            }
            operation.parameters.forEach { parameter ->
               parameter.constraints.forEach {
                  validate(it, typeSystem, parameter)
               }
            }
         }
   }

   private fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget) {
      this.validatingProviders.filter { it.applies(constraint) }
         .forEach { it.validate(constraint, typeSystem, target) }
   }
}

class OperationConstraintConverter(private val expressionList: TaxiParser.ParameterConstraintExpressionListContext, private val paramType: Type, private val typeResolver: NamespaceQualifiedTypeResolver) {
   private val constraintProviders = ConstraintProviders.providers

   fun constraints(): Either<List<CompilationError>, List<Constraint>> {
      return expressionList
         .parameterConstraintExpression().map { buildConstraint(it, paramType, typeResolver) }
         .invertEitherList()
   }

   private fun buildConstraint(constraint: TaxiParser.ParameterConstraintExpressionContext, paramType: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint> {
      return constraintProviders
         .first { it.applies(constraint) }
         .build(constraint, paramType, typeResolver)

   }

}


class MalformedConstraintException(message: String, val constraint: Constraint) : RuntimeException(message)
