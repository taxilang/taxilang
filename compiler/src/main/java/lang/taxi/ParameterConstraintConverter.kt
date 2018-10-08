package lang.taxi

import lang.taxi.services.*
import lang.taxi.types.Field
import lang.taxi.types.ObjectType

interface ConstraintProvider {
    fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean
    fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint
}

interface ValidatingConstraintProvider : ConstraintProvider {
    fun applies(constraint: Constraint): Boolean
    fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget)
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
    fun validateAll(typeSystem: TypeSystem, services: List<Service>) {
        typeSystem.typeList().filterIsInstance<ObjectType>()
                .forEach { type ->
                    type.allFields.forEach { field ->
                        field.constraints.forEach { constraint ->
                            validate(constraint, typeSystem, field)
                        }
                    }
                }

        services.flatMap { it.operations }
                .forEach { operation ->
                    operation.contract?.let { contract ->
                        contract.returnTypeConstraints.forEach {
                            validate(it, typeSystem, contract)
                        }
                    }
                    operation.parameters.forEach { parameter ->
                        parameter.constraints.forEach {
                            validate(it, typeSystem, parameter)
                        }
                    }
                }
    }

    private fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget) {
        this.validatingProviders.filter { it.applies(constraint) }
                .forEach { it.validate(constraint, typeSystem, target) }
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

    override fun validate(constraint: Constraint, typeSystem: TypeSystem, target: ConstraintTarget) {
        val attributeConstraint = constraint as AttributeConstantValueConstraint
        val constrainedType = when (target) {
            is Field -> target.type
            is Parameter -> target.type
            else -> throw IllegalArgumentException("Cannot validate constraint on target of type ${target.javaClass.name}")
        }  as? ObjectType
                ?: throw MalformedConstraintException("Constraints are only supported on Object types.", constraint)

        if (!constrainedType.allFields.any { it.name == attributeConstraint.fieldName }) {
            throw MalformedConstraintException("No field named ${attributeConstraint.fieldName} was found", constraint)
        }
    }

    override fun applies(constraint: TaxiParser.ParameterConstraintExpressionContext): Boolean {
        val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()
        return constraintDefinition?.literal() != null
    }

    override fun build(constraint: TaxiParser.ParameterConstraintExpressionContext, type: Type): Constraint {
        val constraintDefinition = constraint.parameterExpectedValueConstraintExpression()!!
        return AttributeConstantValueConstraint(constraintDefinition.Identifier().text, constraintDefinition.literal().value())
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

class MalformedConstraintException(message: String, val constraint: Constraint) : RuntimeException(message)