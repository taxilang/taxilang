package lang.taxi

import lang.taxi.services.AttributeConstantValueConstraint
import lang.taxi.services.AttributeValueFromParameterConstraint
import lang.taxi.services.Constraint
import lang.taxi.services.ReturnValueDerivedFromParameterConstraint
import lang.taxi.types.ObjectType

interface ConstraintProvider {
    fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean
    fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint
}


class OperationConstraintConverter(val expressionList: TaxiParser.ParameterConstraintExpressionListContext, val paramType: Type) {
    private val constraintProviders = listOf(
            AttributeConstantConstraintProvider(),
            AttributeValueFromParameterConstraintProvider(),
            ReturnValueDerivedFromInputConstraintProvider()
    )

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


class AttributeConstantConstraintProvider : ConstraintProvider {
    override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
        val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()
        return constraintDefinition?.literal() != null
    }

    override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint {
        val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()!!
        return AttributeConstantValueConstraint(constraintDefinition.Identifier().text, constraintDefinition.literal().value())
    }

    private fun validateTargetFieldIsPresentOnType(constraint: TaxiParser.ParameterConstraintExpressionContext, paramType: Type) {
        val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()!!

        val targetField = constraintDefinition.Identifier().text
        if (paramType !is ObjectType) {
            throw CompilationException(constraintDefinition.start, "Constraints are only supported on Object types.")
        }
        val hasField = paramType.fields.any { it.name == targetField }
        if (!hasField) {
            throw CompilationException(constraintDefinition.start, "Constraint field ('$targetField') is not present on type ${paramType.qualifiedName}")
        }
    }
}

class AttributeValueFromParameterConstraintProvider : ConstraintProvider {
    override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
        val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()
        return constraintDefinition?.qualifiedName() != null
    }


    override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint {
        val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()!!
        return AttributeValueFromParameterConstraint(constraintDefinition.Identifier().text, AttributePath(constraintDefinition.qualifiedName()))
    }
}

class ReturnValueDerivedFromInputConstraintProvider : ConstraintProvider {
    override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
        return constraint.operationReturnValueOriginExpression() != null
    }

    override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint {
        return ReturnValueDerivedFromParameterConstraint(AttributePath(constraint.operationReturnValueOriginExpression().qualifiedName()))
    }

}
