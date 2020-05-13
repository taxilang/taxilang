package lang.taxi

import arrow.core.Either
import arrow.core.extensions.either.monad.flatMap
import arrow.core.flatMap
import lang.taxi.services.*
import lang.taxi.types.Field
import lang.taxi.types.ObjectType
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import lang.taxi.utils.leftOr
import lang.taxi.utils.leftOrNull

interface ConstraintProvider {
   fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean
   fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint
}

interface ValidatingConstraintProvider : ConstraintProvider {
   fun applies(constraint: Constraint): Boolean
   fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget): List<CompilationError>
}

object ConstraintProviders {
   val providers = listOf(
      AttributeConstantConstraintProvider(),
      AttributeValueFromParameterConstraintProvider(),
      ReturnValueDerivedFromInputConstraintProvider()
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

class OperationConstraintConverter(val expressionList: TaxiParser.ParameterConstraintExpressionListContext, val paramType: Type) {
   private val constraintProviders = ConstraintProviders.providers

   fun constraints(): List<Constraint> {
      return expressionList
         .parameterConstraintExpression().map { buildConstraint(it, paramType) }
   }

   private fun buildConstraint(constraint: TaxiParser.ParameterConstraintExpressionContext, paramType: Type): Constraint {
      return constraintProviders
         .first { it.applies(constraint) }
         .build(constraint, paramType)

   }

}


class AttributeConstantConstraintProvider : ValidatingConstraintProvider {
   override fun applies(constraint: Constraint): Boolean {
      return constraint is AttributeConstantValueConstraint
   }

   override fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget): List<CompilationError> {
      val attributeConstraint = constraint as AttributeConstantValueConstraint
      val constrainedTypeEither = getUnderlyingType(when (target) {
         is Field -> target.type
         is Parameter -> target.type
         else -> throw IllegalArgumentException("Cannot validate constraint on target of type ${target.javaClass.name}")
      }, constraint, target)

      val error: CompilationError? = constrainedTypeEither
         .flatMap { constrainedType ->
            if (!constrainedType.allFields.any { it.name == attributeConstraint.fieldName }) {
               Either.left(MalformedConstraint.from("No field named ${attributeConstraint.fieldName} was found", constraint))
            } else {
               Either.right(constrainedType)
            }
         }.leftOrNull()
      return listOfNotNull(error)
   }

   // Unwraps type aliases, if present
   private fun getUnderlyingType(type: Type, constraint: AttributeConstantValueConstraint, target: ConstraintTarget): Either<CompilationError, ObjectType> {
      return when (type) {
         is TypeAlias -> getUnderlyingType(type.aliasType!!, constraint, target)
         is ObjectType -> Either.right(type)
         else -> Either.left(MalformedConstraint.from("Constraint for field ${constraint.fieldName} on ${target.description} is malformed - constraints are only supported on Object types.", constraint))
      }
   }

   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()
      return constraintDefinition?.literal() != null
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint {
      val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()!!
      return AttributeConstantValueConstraint(constraintDefinition.Identifier().text, constraintDefinition.literal().value(),
         constraint.toCompilationUnits()
      )
   }

}

class AttributeValueFromParameterConstraintProvider : ConstraintProvider {
   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()
      return constraintDefinition?.qualifiedName() != null
   }


   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint {
      val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()!!
      return AttributeValueFromParameterConstraint(
         constraintDefinition.Identifier().text,
         constraintDefinition.qualifiedName().toAttributePath(),
         constraint.toCompilationUnits()
      )
   }
}

class ReturnValueDerivedFromInputConstraintProvider : ConstraintProvider {
   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      return constraint.operationReturnValueOriginExpression() != null
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint {
      return ReturnValueDerivedFromParameterConstraint(constraint.operationReturnValueOriginExpression().qualifiedName().toAttributePath(),
         constraint.toCompilationUnits()
      )
   }

}

object MalformedConstraint {
   fun from(message: String, constraint: Constraint): CompilationError {
      val sourceName = constraint.compilationUnits.first().source.sourceName
      // TODO : Find a way to map the error back to the actual position in the source.
      return CompilationError(SourceLocation.UNKNOWN_POSITION, message, sourceName)
   }
}

