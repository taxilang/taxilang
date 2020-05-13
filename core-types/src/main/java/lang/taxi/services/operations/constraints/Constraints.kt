package lang.taxi.services.operations.constraints

interface ConstraintTarget {
   val constraints: List<Constraint>

   val description: String
}

interface Constraint {
   fun asTaxi(): String
}
