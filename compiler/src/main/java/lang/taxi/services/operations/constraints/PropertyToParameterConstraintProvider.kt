package lang.taxi.services.operations.constraints

import arrow.core.Either
import lang.taxi.*
import lang.taxi.services.Parameter
import lang.taxi.types.*

class PropertyToParameterConstraintProvider : ValidatingConstraintProvider {
   override fun applies(constraint: Constraint): Boolean {
      return constraint is NamedFieldConstantValueConstraint
   }

   override fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget) {
      val attributeConstraint = constraint as NamedFieldConstantValueConstraint
      val constrainedType = getUnderlyingType(when (target) {
         is Field -> target.type
         is Parameter -> target.type
         else -> throw IllegalArgumentException("Cannot validate constraint on target of type ${target.javaClass.name}")
      }, constraint, target)

      if (!constrainedType.allFields.any { it.name == attributeConstraint.fieldName }) {
         throw MalformedConstraintException("No field named ${attributeConstraint.fieldName} was found", constraint)
      }
   }

   // Unwraps type aliases, if present
   private fun getUnderlyingType(type: Type, constraint: NamedFieldConstantValueConstraint, target: ConstraintTarget): ObjectType {
      return when (type) {
         is TypeAlias -> getUnderlyingType(type.aliasType!!, constraint, target)
         is ObjectType -> type
         else -> throw MalformedConstraintException("Constraint for field ${constraint.fieldName} on ${target.description} is malfored - onstraints are only supported on Object types.", constraint)
      }
   }

   override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
      return constraint.propertyToParameterConstraintExpression() != null
   }

   override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type, typeResolver:NamespaceQualifiedTypeResolver): Either<CompilationError, Constraint> {
      val constraintDefinition = constraint.propertyToParameterConstraintExpression()!!
      // Could be either a type name or a field name
      val operator = Operator.parse(constraintDefinition.comparisonOperator().text)
      val propertyIdentifierText = constraintDefinition.qualifiedName().asDotJoinedPath()
      val propertyIdentifier = if (propertyIdentifierText.startsWith("this.")) {
         // TODO : Is the path that's been requested an attribute on the type?
         // If not, return a compiler error
         Either.right(PropertyFieldNameIdentifier(AttributePath.from(propertyIdentifierText)))
      } else {
         typeResolver.resolve(propertyIdentifierText, constraint).map { propertyType -> PropertyTypeIdentifier(propertyType.toQualifiedName()) }
      }
      val comparisonValue = when {
         constraintDefinition.literal() != null -> ConstantValueExpression(constraintDefinition.literal().value())
         constraintDefinition.qualifiedName() != null -> RelativeValueExpression(AttributePath.from(constraintDefinition.qualifiedName().asDotJoinedPath()))
         else -> error("Could not parse a value expression from token")
      }
      return propertyIdentifier.map { identifier ->
         PropertyToParameterConstraint(identifier,operator,comparisonValue)
      }
   }

}

private fun List<TaxiParser.QualifiedNameContext>.asDotJoinedPath(): String {
   return this.flatMap { it.Identifier() }.joinToString(".") { it.text }
}
