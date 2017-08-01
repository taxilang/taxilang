package lang.taxi

import lang.taxi.services.AttributeConstantValueConstraint
import lang.taxi.services.AttributeValueFromParameterConstraint
import lang.taxi.services.Constraint
import lang.taxi.types.ObjectType

interface ConstraintProvider {
    fun applies(constraint: TaxiParser.OperationParameterConstraintExpressionContext): Boolean
    fun build(constraint: TaxiParser.OperationParameterConstraintExpressionContext, type: Type): Constraint
}


class OperationConstraintConverter(val expressionList: TaxiParser.OperationParameterConstraintExpressionListContext, val paramType: Type) {
    private val constraintProviders = listOf(
            AttributeConstantConstraintProvider(),
            AttributeValueFromParameterConstraintProvider()
    )

    fun constraints(): List<Constraint> {
        return expressionList
                .operationParameterConstraintExpression().map { buildConstraint(it, paramType) }
    }

    private fun buildConstraint(constraint: TaxiParser.OperationParameterConstraintExpressionContext, paramType: Type): Constraint {
        validateTargetFieldIsPresentOnType(constraint, paramType)
        return constraintProviders
                .first { it.applies(constraint) }
                .build(constraint, paramType)

    }

    private fun validateTargetFieldIsPresentOnType(it: TaxiParser.OperationParameterConstraintExpressionContext, paramType: Type) {
        val targetField = it.Identifier().text
        if (paramType !is ObjectType) {
            throw CompilationException(it.start, "Constraints are only supported on Object types.")
        }
        val hasField = paramType.fields.any { it.name == targetField }
        if (!hasField) {
            throw CompilationException(it.start, "Constraint field ('$targetField') is not present on type ${paramType.qualifiedName}")
        }
    }
}


class AttributeConstantConstraintProvider : ConstraintProvider {
    override fun applies(constraint: TaxiParser.OperationParameterConstraintExpressionContext): Boolean {
        return constraint.expression().primary().literal() != null
    }

    override fun build(constraint: TaxiParser.OperationParameterConstraintExpressionContext, type: Type): Constraint {
        return AttributeConstantValueConstraint(constraint.Identifier().text, constraint.expression().primary().literal().value())
    }
}

class AttributeValueFromParameterConstraintProvider : ConstraintProvider {
    override fun applies(constraint: TaxiParser.OperationParameterConstraintExpressionContext): Boolean {
        return constraint.expression().primary().Identifier() != null
    }

    override fun build(constraint: TaxiParser.OperationParameterConstraintExpressionContext, type: Type): Constraint {
        return AttributeValueFromParameterConstraint(constraint.Identifier().text, constraint.expression().primary().Identifier().text)
    }

}
