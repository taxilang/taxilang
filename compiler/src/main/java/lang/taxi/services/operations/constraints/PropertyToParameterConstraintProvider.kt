package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.flatMap
import lang.taxi.*
import lang.taxi.services.Operation
import lang.taxi.services.OperationContract
import lang.taxi.services.Parameter
import lang.taxi.types.*
import lang.taxi.utils.leftOr

class PropertyToParameterConstraintProvider : ValidatingConstraintProvider {
   override fun applies(constraint: Constraint): Boolean {
      return constraint is PropertyToParameterConstraint
   }

   override fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget, constraintDeclarationSite: Compiled): List<CompilationError> {
      val attributeConstraint = constraint as PropertyToParameterConstraint
      val validationResult = getUnderlyingType(when (target) {
         is Field -> target.type
         is Parameter -> target.type
         is OperationContract -> target.returnType
         else -> throw IllegalArgumentException("Cannot validate constraint on target of type ${target.javaClass.name}")
      }, constraint, target)
         .mapLeft { listOf(it) }
         .flatMap { constrainedType ->
            val lhsValidationError = when (attributeConstraint.propertyIdentifier) {
               is PropertyFieldNameIdentifier -> validate(constraint.propertyIdentifier as PropertyFieldNameIdentifier, constrainedType, constraint)
               is PropertyTypeIdentifier -> validate(constraint.propertyIdentifier as PropertyTypeIdentifier, constrainedType, constraint)
            }

            val rhsValidationError = when (attributeConstraint.expectedValue) {
               is ConstantValueExpression -> null // Not sure that to validate here?
               is RelativeValueExpression -> validate(constraint.expectedValue as RelativeValueExpression, target, constraint, constraintDeclarationSite)
            }

            val errors = listOfNotNull(lhsValidationError,rhsValidationError)
            if (errors.isEmpty()) {
               Either.right(constraint)
            } else {
               Either.left(errors)
            }
         }

      return validationResult.leftOr(emptyList())
   }

   private fun validate(relativeValueExpression: RelativeValueExpression, target: ConstraintTarget, constraint: PropertyToParameterConstraint, constraintDeclarationSite: Compiled): CompilationError? {
      return if (target !is OperationContract) {
         // TODO :  Need to understand what other scnearios would look like
         CompilationError(constraint, "These types of constraints can only be applied to return types of operations at present")
      } else {
         if (constraintDeclarationSite !is Operation) {
            CompilationError(constraint, "Expected that constraint would be declared in an operation.  This is likely a bug in the compiler")
         } else {
            if (relativeValueExpression.path.canResolve(constraintDeclarationSite.parameters)) {
               null
            } else {
               CompilationError(constraint, "Operation ${constraintDeclarationSite.name} does not declare a parameter with name ${relativeValueExpression.path.path}")
            }
         }
      }
   }

   private fun validate(property: PropertyTypeIdentifier, constrainedType: ObjectType, constraint: Constraint): CompilationError? {
      val fields = constrainedType.fieldsWithType(property.type)
      return when {
         fields.isEmpty() -> CompilationError(constraint, "Type ${constrainedType.qualifiedName} does not have a field with type ${property.type}")
         fields.size > 1 -> CompilationError(constraint, "Type ${constrainedType.qualifiedName} has multiple fields with type ${property.type}.  This is ambiguous, and the constraint is invalid.")
         else -> null
      }
   }

   private fun validate(propertyName: PropertyFieldNameIdentifier, constrainedType: ObjectType, constraint: Constraint): CompilationError? {
      val propertyPath = propertyName.name.path
      return if (!constrainedType.hasField(propertyPath)) { // TODO : This should take the path directly, and evaluate nested paths
         CompilationError(constraint, "Type ${constrainedType.qualifiedName} does not contain a property $propertyPath")
      } else {
         null
      }
   }

   // Unwraps type aliases, if present
   private fun getUnderlyingType(type: Type, constraint: PropertyToParameterConstraint, target: ConstraintTarget): Either<CompilationError, ObjectType> {
      return when (type) {
         is TypeAlias -> getUnderlyingType(type.aliasType!!, constraint, target)
         is ArrayType -> getUnderlyingType(type.type, constraint,target)
         is ObjectType -> Either.right(type)
         else -> Either.left(MalformedConstraint.from("Constraint for ${constraint.propertyIdentifier.description} on ${target.description} is malformed - constraints are only supported on Object types.", constraint))
      }
   }

   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      return constraint.propertyToParameterConstraintExpression() != null
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint> {
      val constraintDefinition = constraint.propertyToParameterConstraintExpression()!!
      // Could be either a type name or a field name
      val operator = Operator.parse(constraintDefinition.comparisonOperator().text)

      return parseLhs(constraintDefinition, typeResolver).flatMap { propertyIdentifier ->
         parseRhs(constraintDefinition, typeResolver).map { valueExpression ->
            PropertyToParameterConstraint(propertyIdentifier, operator, valueExpression, constraint.toCompilationUnits())
         }
      }
   }


   private fun parseLhs(constraintDefinition: TaxiParser.PropertyToParameterConstraintExpressionContext, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, PropertyIdentifier> {
      val lhs = constraintDefinition.propertyToParameterConstraintLhs()
      val typeOrFieldQualifiedName = lhs.qualifiedName().asDotJoinedPath()
      return when {
         lhs.propertyFieldNameQualifier() != null -> {
            Either.right(PropertyFieldNameIdentifier(AttributePath.from(typeOrFieldQualifiedName)))
         }
         else -> typeResolver.resolve(typeOrFieldQualifiedName, lhs).map { propertyType -> PropertyTypeIdentifier(propertyType.toQualifiedName()) }
      }
   }

   private fun parseRhs(constraintDefinition: TaxiParser.PropertyToParameterConstraintExpressionContext, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError, ValueExpression> {
      val rhs = constraintDefinition.propertyToParameterConstraintRhs()
      return when {
         rhs.literal() != null -> Either.right(ConstantValueExpression(rhs.literal().value()))
         rhs.qualifiedName() != null -> Either.right(RelativeValueExpression(AttributePath.from(rhs.qualifiedName().asDotJoinedPath())))
         else -> error("Unhandled scenario parsing rhs of constraint")
      }
   }


}

private fun TaxiParser.QualifiedNameContext.asDotJoinedPath(): String {
   return this.Identifier().joinToString(".") { it.text }
}
