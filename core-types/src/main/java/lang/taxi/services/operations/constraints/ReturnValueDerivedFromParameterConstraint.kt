package lang.taxi.services.operations.constraints

import lang.taxi.ImmutableEquality
import lang.taxi.types.AttributePath
import lang.taxi.types.CompilationUnit

data class ReturnValueDerivedFromParameterConstraint(val attributePath: AttributePath, override val compilationUnits: List<CompilationUnit>) : Constraint {
   override fun asTaxi(): String = "from $path"

   private val equality = ImmutableEquality(this, ReturnValueDerivedFromParameterConstraint::attributePath)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()


   val path = attributePath.path
}
