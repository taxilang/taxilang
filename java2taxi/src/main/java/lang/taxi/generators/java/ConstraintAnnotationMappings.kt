package lang.taxi.generators.java

import lang.taxi.annotations.ConstraintAnnotationModel
import lang.taxi.annotations.ResponseContract
import lang.taxi.services.AttributeConstantValueConstraint
import lang.taxi.services.AttributeValueFromParameterConstraint
import lang.taxi.services.Constraint
import lang.taxi.services.ReturnValueDerivedFromParameterConstraint

private val defaultConverters = listOf(
        AttributeConstantConstaintAnnotationCoverter(),
        AttributeValueFromParameterConstraintConvert()
)

class ConstraintAnnotationMapper(val converters: List<ConstraintAnnotationConverter> = defaultConverters) {
    fun convert(constraints: List<lang.taxi.annotations.Constraint>): List<Constraint> {
        return doConvert(constraints.map { ConstraintAnnotationModel(it) })
    }

    private fun doConvert(constraints: List<ConstraintAnnotationModel>): List<Constraint> {
        return constraints.flatMap { constraint ->
            converters.filter { it.canProvide(constraint) }
                    .map { it.provide(constraint) }
        }
    }

    fun convert(contract: ResponseContract): List<Constraint> {
        val basedOn = ReturnValueDerivedFromParameterConstraint(contract.basedOn)
        val mappedConstraints = doConvert(contract.constraints
                .map { ConstraintAnnotationModel(it) })
        // Note: basedOn MUST come first to ensure order
        return listOf(basedOn) + mappedConstraints
    }
}

interface ConstraintAnnotationConverter {
    fun canProvide(constraint: ConstraintAnnotationModel): Boolean
    fun provide(constraint: ConstraintAnnotationModel): Constraint
}

class AttributeConstantConstaintAnnotationCoverter : ConstraintAnnotationConverter {
    override fun canProvide(constraint: ConstraintAnnotationModel): Boolean {
        return constraint.value.removeSpaces().matches("(\\w+)='(\\w+)'".toRegex())
    }

    override fun provide(constraint: ConstraintAnnotationModel): AttributeConstantValueConstraint {
        val parts = constraint.value.split("=")
        return AttributeConstantValueConstraint(parts[0].trim(), parts[1].trim().removeSurrounding("'"))
    }
}

class AttributeValueFromParameterConstraintConvert : ConstraintAnnotationConverter {
    override fun canProvide(constraint: ConstraintAnnotationModel): Boolean {
        // Note the difference here (from AttributeConstantConstaintAnnotationCoverter)
        // is that we're looking for cases WITHOUT quotes
        return constraint.value.removeSpaces().matches("(\\w+)=(\\w+)".toRegex())
    }

    override fun provide(constraint: ConstraintAnnotationModel): Constraint {
        val parts = constraint.value.split("=")
        return AttributeValueFromParameterConstraint(parts[0].trim(), parts[1].trim())
    }

}

fun String.removeSpaces(): String {
    return this.replace(" ", "")
}
