package lang.taxi.generators.java

import lang.taxi.Operator
import lang.taxi.annotations.ConstraintAnnotationModel
import lang.taxi.annotations.ResponseContract
import lang.taxi.services.operations.constraints.ConstantValueExpression
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.PropertyFieldNameIdentifier
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.RelativeValueExpression
import lang.taxi.services.operations.constraints.ReturnValueDerivedFromParameterConstraint
import lang.taxi.types.AttributePath
import lang.taxi.types.CompilationUnit

private val defaultConverters = listOf(
   AttributeConstantConstraintAnnotationConverter(),
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
      val basedOn = ReturnValueDerivedFromParameterConstraint(AttributePath.from(contract.basedOn), listOf(CompilationUnit.unspecified()))
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

class AttributeConstantConstraintAnnotationConverter : ConstraintAnnotationConverter {
   override fun canProvide(constraint: ConstraintAnnotationModel): Boolean {
      return constraint.value.removeSpaces()
         .removePrefix("this.")
         .matches("(\\w+)='(\\w+)'".toRegex())
   }

   override fun provide(constraint: ConstraintAnnotationModel): PropertyToParameterConstraint {
      val parts = constraint.value.split("=")
      return PropertyToParameterConstraint(
         propertyIdentifier = PropertyFieldNameIdentifier(parts[0].trim().removePrefix("this.")),
         operator = Operator.EQUAL, // TODO : Support more operations
         expectedValue = ConstantValueExpression(parts[1].trim().removeSurrounding("'")),
         compilationUnits = listOf(CompilationUnit.unspecified())
      )
   }
}

class AttributeValueFromParameterConstraintConvert : ConstraintAnnotationConverter {
   override fun canProvide(constraint: ConstraintAnnotationModel): Boolean {
      // Note the difference here (from AttributeConstantConstaintAnnotationCoverter)
      // is that we're looking for cases WITHOUT quotes
      return constraint.value
         .removeSpaces()
         .removePrefix("this.")
         .matches("(\\w+)=(\\w+)".toRegex())
   }

   override fun provide(constraint: ConstraintAnnotationModel): Constraint {
      val parts = constraint.value.split("=")
      return PropertyToParameterConstraint(
         propertyIdentifier = PropertyFieldNameIdentifier(parts[0].trim()),
         operator = Operator.EQUAL, // TODO : Support more operations
         expectedValue = RelativeValueExpression(AttributePath.from(parts[1].trim())),
         compilationUnits = listOf(CompilationUnit.unspecified())
      )
   }

}

fun String.removeSpaces(): String {
   return this.replace(" ", "")
}
