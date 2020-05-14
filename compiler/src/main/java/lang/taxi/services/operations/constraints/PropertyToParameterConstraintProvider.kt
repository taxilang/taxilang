package lang.taxi.services.operations.constraints

import arrow.core.Either
import arrow.core.flatMap
import lang.taxi.*
import lang.taxi.services.Parameter
import lang.taxi.types.*

class PropertyToParameterConstraintProvider : ValidatingConstraintProvider {
   override fun applies(constraint: Constraint): Boolean {
      return constraint is PropertyToParameterConstraint
   }

   override fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget):List<CompilationError> {
      // TODO
      return emptyList()
//      val attributeConstraint = constraint as PropertyToParameterConstraint
//      val constrainedType = getUnderlyingType(when (target) {
//         is Field -> target.type
//         is Parameter -> target.type
//         else -> throw IllegalArgumentException("Cannot validate constraint on target of type ${target.javaClass.name}")
//      }, constraint, target)

//      TODO()
//      if (!constrainedType.allFields.any { it.name == attributeConstraint.fieldName }) {
//         MalformedConstraint.from("No field named ${attributeConstraint.fieldName} was found", constraint)
//      }
   }

   // Unwraps type aliases, if present
   private fun getUnderlyingType(type: Type, constraint: PropertyToParameterConstraint, target: ConstraintTarget): Either<CompilationError,ObjectType> {
      return when (type) {
         is TypeAlias -> getUnderlyingType(type.aliasType!!, constraint, target)
         is ObjectType -> Either.right(type)
         else -> Either.left(MalformedConstraint.from("Constraint for ${constraint.propertyIdentifier.description} on ${target.description} is malformed - constraints are only supported on Object types.", constraint))
      }
   }

   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      return constraint.propertyToParameterConstraintExpression() != null
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver:NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint> {
      val constraintDefinition = constraint.propertyToParameterConstraintExpression()!!
      // Could be either a type name or a field name
      val operator = Operator.parse(constraintDefinition.comparisonOperator().text)

      return parseLhs(constraintDefinition, typeResolver).flatMap { propertyIdentifier ->
         parseRhs(constraintDefinition, typeResolver).map { valueExpression ->
            PropertyToParameterConstraint(propertyIdentifier,operator,valueExpression, constraint.toCompilationUnits())
         }
      }
   }


   private fun parseLhs(constraintDefinition: TaxiParser.PropertyToParameterConstraintExpressionContext, typeResolver: NamespaceQualifiedTypeResolver): Either<CompilationError,PropertyIdentifier> {
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
   return this.Identifier().joinToString(".") { it.text}
}
