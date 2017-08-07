package lang.taxi

import lang.taxi.services.AttributeConstantValueConstraint
import lang.taxi.services.AttributeValueFromParameterConstraint
import lang.taxi.services.Constraint
import lang.taxi.services.ReturnValueDerivedFromParameterConstraint
import lang.taxi.types.ObjectType

interface ConstraintProvider {
    fun applies(constraint: TaxiParser.OperationParameterConstraintExpressionContext): Boolean
    fun build(constraint: TaxiParser.OperationParameterConstraintExpressionContext, type: Type): Constraint
}


class OperationConstraintConverter(val expressionList: TaxiParser.OperationParameterConstraintExpressionListContext, val paramType: Type) {
    private val constraintProviders = listOf(
            AttributeConstantConstraintProvider(),
            AttributeValueFromParameterConstraintProvider(),
            ReturnValueDerivedFromInputConstraintProvider()
    )

    fun constraints(): List<Constraint> {
        return expressionList
                .operationParameterConstraintExpression().map { buildConstraint(it, paramType) }
    }

    private fun buildConstraint(constraint: TaxiParser.OperationParameterConstraintExpressionContext, paramType: Type): Constraint {
        return constraintProviders
                .first { it.applies(constraint) }
                .build(constraint, paramType)

    }

}


class AttributeConstantConstraintProvider : ConstraintProvider {
    override fun applies(constraint: TaxiParser.OperationParameterConstraintExpressionContext): Boolean {
        val constraintDefinition = constraint.operationParameterExpectedValueConstraintExpression()
        return constraintDefinition != null && constraintDefinition.expression().primary().literal() != null
    }

    override fun build(constraint: TaxiParser.OperationParameterConstraintExpressionContext, type: Type): Constraint {
        val constraintDefinition = constraint.operationParameterExpectedValueConstraintExpression()!!
        return AttributeConstantValueConstraint(constraintDefinition.Identifier().text, constraintDefinition.expression().primary().literal().value())
    }

    private fun validateTargetFieldIsPresentOnType(constraint: TaxiParser.OperationParameterConstraintExpressionContext, paramType: Type) {
        val constraintDefinition = constraint.operationParameterExpectedValueConstraintExpression()!!

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
    override fun applies(constraint: TaxiParser.OperationParameterConstraintExpressionContext): Boolean {
        val constraintDefinition = constraint.operationParameterExpectedValueConstraintExpression()
        return constraintDefinition != null && constraintDefinition.expression().primary().Identifier() != null
    }


    override fun build(constraint: TaxiParser.OperationParameterConstraintExpressionContext, type: Type): Constraint {
        val constraintDefinition = constraint.operationParameterExpectedValueConstraintExpression()!!
        return AttributeValueFromParameterConstraint(constraintDefinition.Identifier().text, constraintDefinition.expression().primary().Identifier().text)
    }
}

class ReturnValueDerivedFromInputConstraintProvider : ConstraintProvider {
    override fun applies(constraint: TaxiParser.OperationParameterConstraintExpressionContext): Boolean {
        return constraint.operationReturnValueOriginExpression() != null
    }

    override fun build(constraint: TaxiParser.OperationParameterConstraintExpressionContext, type: Type): Constraint {
        return ReturnValueDerivedFromParameterConstraint(constraint.operationReturnValueOriginExpression().Identifier().text)
    }

}
