package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import lang.taxi.*
import lang.taxi.query.asDotJoinedPath
import lang.taxi.services.Operation
import lang.taxi.services.OperationContract
import lang.taxi.services.Parameter
import lang.taxi.types.*
import lang.taxi.utils.leftOr
import lang.taxi.utils.log

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

            val errors = listOfNotNull(lhsValidationError, rhsValidationError)
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
       //  fields.isEmpty() -> CompilationError(constraint, "Type ${constrainedType.qualifiedName} does not have a field with type ${property.type}")
         fields.size > 1 -> {
            log().warn(fields.joinToString { it.name })
            CompilationError(constraint, "Type ${constrainedType.qualifiedName} has multiple fields with type ${property.type}.  This is ambiguous, and the constraint is invalid.")
         }
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
         is ArrayType -> getUnderlyingType(type.type, constraint, target)
         is StreamType -> getUnderlyingType(type.type, constraint, target)
         is ObjectType -> Either.right(type)
         else -> Either.left(MalformedConstraint.from("Constraint for ${constraint.propertyIdentifier.description} type ${type} on ${target.description} is malformed - constraints are only supported on Object types.", constraint))
      }
   }

   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      return constraint.propertyToParameterConstraintExpression() != null
   }

   fun build(propertyToParameterConstraintExpression: TaxiParser.PropertyToParameterConstraintExpressionContext,
             typeResolver: NamespaceQualifiedTypeResolver): Either<List<CompilationError>, PropertyToParameterConstraint> {
      val operator = Operator.parse(propertyToParameterConstraintExpression.comparisonOperator().text)
      return parseLhs(typeResolver, propertyToParameterConstraintExpression).flatMap { propertyIdentifier ->
         parseRhs(propertyToParameterConstraintExpression).map { valueExpression ->
            PropertyToParameterConstraint(propertyIdentifier, operator, valueExpression, propertyToParameterConstraintExpression.toCompilationUnits())
         }
      }
   }

   fun build(type: Type, typeResolver: NamespaceQualifiedTypeResolver, context: TaxiParser.ParameterConstraintExpressionContext): Either<List<CompilationError>, Constraint> {
      // Could be either a type name or a field name
      val propertyToParameterConstraintExpression = context.propertyToParameterConstraintExpression()
      val operator = Operator.parse(propertyToParameterConstraintExpression.comparisonOperator().text)

      return parseLhs(typeResolver, propertyToParameterConstraintExpression).flatMap { propertyIdentifier ->
         parseRhs(propertyToParameterConstraintExpression).map { valueExpression ->
            PropertyToParameterConstraint(propertyIdentifier, operator, valueExpression, context.toCompilationUnits())
         }
      }
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver: NamespaceQualifiedTypeResolver): Either<List<CompilationError>, Constraint> {
      return build(type,typeResolver,constraint)
   }


   private fun parseLhs(typeResolver: NamespaceQualifiedTypeResolver, context: TaxiParser.PropertyToParameterConstraintExpressionContext): Either<List<CompilationError>, PropertyIdentifier> {
      val typeOrFieldQualifiedName = context.propertyToParameterConstraintLhs().qualifiedName().asDotJoinedPath()
      val propertyFieldNameQualifier = context.propertyToParameterConstraintLhs().propertyFieldNameQualifier()?.text
      return when {
         propertyFieldNameQualifier != null -> {
            Either.right(PropertyFieldNameIdentifier(AttributePath.from(typeOrFieldQualifiedName.removePrefix("this."))))
         }
         else -> typeResolver.resolve(typeOrFieldQualifiedName, context).map { propertyType -> PropertyTypeIdentifier(propertyType.toQualifiedName()) }
      }
   }

   private fun parseRhs(context: TaxiParser.PropertyToParameterConstraintExpressionContext): Either<List<CompilationError>, ValueExpression> {
      val rhs = context.propertyToParameterConstraintRhs()
      val literal = rhs.literal()?.value()
      val attributePath = rhs.qualifiedName()?.asDotJoinedPath()?.let { AttributePath.from(it) }
      return when {
         literal != null -> Either.right(ConstantValueExpression(literal))
         attributePath != null -> Either.right(RelativeValueExpression(attributePath))
         else -> error("Unhandled scenario parsing rhs of constraint")
      }
   }
}
