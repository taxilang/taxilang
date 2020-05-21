package lang.taxi.services.operations.constraints

import lang.taxi.types.Compiled

interface ConstraintTarget {
   val constraints: List<Constraint>

   val description: String
}

interface Constraint : Compiled {
   fun asTaxi(): String
}
