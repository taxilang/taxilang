package lang.taxi.services.operations.constraints


import arrow.core.Either
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.NamespaceQualifiedTypeResolver
import lang.taxi.TaxiParser
import lang.taxi.TypeSystem
import lang.taxi.services.Service
import lang.taxi.types.Compiled
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.utils.flattenErrors
import lang.taxi.utils.invertEitherList


interface ConstraintProvider {
   fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean
   fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<List<CompilationError>, Constraint>
}

interface ValidatingConstraintProvider : ConstraintProvider {
   fun applies(constraint: Constraint): Boolean
   fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget, constraintDeclarationSite: Compiled): List<CompilationError>
}

object ConstraintProviders {
   val providers = listOf(
      ReturnValueDerivedFromInputConstraintProvider(),
      PropertyToParameterConstraintProvider()
   )
}

class ConstraintValidator(providers: List<ConstraintProvider> = ConstraintProviders.providers) {
   private val validatingProviders = providers.filterIsInstance<ValidatingConstraintProvider>()
   fun validateAll(typeSystem: TypeSystem, services: List<Service>): List<CompilationError> {
      val typeErrors = typeSystem.typeList().filterIsInstance<ObjectType>()
         .flatMap { type ->
            type.allFields.flatMap { field ->
               field.constraints.flatMap { constraint ->
                  validate(constraint, typeSystem, field, type)
               }
            }
         }

      val serviceErrors = services.flatMap { it.operations }
         .flatMap { operation ->
            val contractErrors = operation.contract?.let { contract ->
               contract.returnTypeConstraints.flatMap {
                  validate(it, typeSystem, contract, operation)
               }
            } ?: emptyList()

            val paramErrors = operation.parameters.flatMap { parameter ->
               parameter.constraints.flatMap {
                  validate(it, typeSystem, parameter, operation)
               }
            }
            contractErrors + paramErrors
         }
      return typeErrors + serviceErrors
   }

   private fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget, constraintDeclarationSite: Compiled): List<CompilationError> {
      return this.validatingProviders.filter { it.applies(constraint) }
         .flatMap { it.validate(constraint, typeSystem, target, constraintDeclarationSite) }
   }
}

class OperationConstraintConverter(
   private val expressionList: TaxiParser.ParameterConstraintExpressionListContext?,
   private val paramType: Type,
   private val namespaceQualifiedTypeResolver: NamespaceQualifiedTypeResolver
) {
   private val constraintProviders = ConstraintProviders.providers

   fun constraints(): Either<List<CompilationError>, List<Constraint>> {
      return expressionList
         ?.parameterConstraintExpression()
         // Formats are expressed as constraints, but we handle them elsewhere,
         // so filter them out.  A good indication that this isn't the correct way
         // to handle this.
         ?.filter { it.propertyFormatExpression() == null }
         ?.map { buildConstraint(it, paramType, namespaceQualifiedTypeResolver) }
         ?.invertEitherList()?.flattenErrors() ?: emptyList<Constraint>().right()
   }

   private fun buildConstraint(constraint: TaxiParser.ParameterConstraintExpressionContext, paramType: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<List<CompilationError>, Constraint> {
      return constraintProviders
         .first { it.applies(constraint) }
         .build(constraint, paramType, typeResolver)

   }
}

object MalformedConstraint {
   fun from(message: String, constraint: Constraint): CompilationError {
      return CompilationError(constraint, message)
   }
}
