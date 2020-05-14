package lang.taxi.services.operations.constraints


import arrow.core.Either
import lang.taxi.*
import lang.taxi.services.Service
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.utils.invertEitherList


interface ConstraintProvider {
   fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean
   fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint>
}

interface ValidatingConstraintProvider : ConstraintProvider {
   fun applies(constraint: Constraint): Boolean
   fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget): List<CompilationError>
}

object ConstraintProviders {
   val providers = listOf(
      ReturnValueDerivedFromInputConstraintProvider(),
      PropertyToParameterConstraintProvider()
   )
}

class ConstraintValidator(providers: List<ConstraintProvider> = ConstraintProviders.providers) {
   private val validatingProviders = providers.filterIsInstance<ValidatingConstraintProvider>()
   fun validateAll(typeSystem: TypeSystem, services: List<Service>):List<CompilationError> {
      val typeErrors = typeSystem.typeList().filterIsInstance<ObjectType>()
         .flatMap { type ->
            type.allFields.flatMap { field ->
               field.constraints.flatMap { constraint ->
                  validate(constraint, typeSystem, field)
               }
            }
         }

      val serviceErrors = services.flatMap { it.operations }
         .flatMap { operation ->
            operation.contract?.let { contract ->
               contract.returnTypeConstraints.forEach {
                  validate(it, typeSystem, contract)
               }
            }
            operation.parameters.flatMap { parameter ->
               parameter.constraints.flatMap {
                  validate(it, typeSystem, parameter)
               }
            }
         }
      return typeErrors + serviceErrors
   }

   private fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget): List<CompilationError> {
      return this.validatingProviders.filter { it.applies(constraint) }
         .flatMap { it.validate(constraint, typeSystem, target) }
   }
}

class OperationConstraintConverter(
   private val expressionList: TaxiParser.ParameterConstraintExpressionListContext,
   private val paramType: Type,
   private val namespaceQualifiedTypeResolver: NamespaceQualifiedTypeResolver
) {
   private val constraintProviders = ConstraintProviders.providers

   fun constraints(): Either<List<CompilationError>,List<Constraint>> {
      return expressionList
         .parameterConstraintExpression().map { buildConstraint(it, paramType, namespaceQualifiedTypeResolver) }
         .invertEitherList()
   }

   private fun buildConstraint(constraint: TaxiParser.ParameterConstraintExpressionContext, paramType: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint>  {
      return constraintProviders
         .first { it.applies(constraint) }
         .build(constraint, paramType, typeResolver)

   }

}

object MalformedConstraint {
   fun from(message: String, constraint: Constraint): CompilationError {
      val sourceName = constraint.compilationUnits.first().source.sourceName
      // TODO : Find a way to map the error back to the actual position in the source.
      return CompilationError(SourceLocation.UNKNOWN_POSITION, message, sourceName)
   }
}
