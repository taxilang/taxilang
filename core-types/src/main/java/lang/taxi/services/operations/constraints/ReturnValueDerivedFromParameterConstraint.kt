package lang.taxi.services.operations.constraints

import lang.taxi.types.AttributePath

data class ReturnValueDerivedFromParameterConstraint(val attributePath: AttributePath) : Constraint {
   override fun asTaxi(): String = "from $path"

   val path = attributePath.path
}
