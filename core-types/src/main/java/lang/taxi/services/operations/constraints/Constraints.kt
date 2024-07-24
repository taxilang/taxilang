package lang.taxi.services.operations.constraints

import lang.taxi.expressions.Expression
import lang.taxi.types.Compiled

interface ConstraintTarget {
   val constraints: List<Constraint>

   val description: String
}

interface Constraint : Compiled {
   fun asTaxi(): String

   /**
    * Indicates if this constraint can satisfy the
    * requested constraint.
    *
    * Note that order is important here.
    * Where a.satisfiesRequestedConstraint(b) == true,
    * b.satisfiesRequestedConstraint(a) may not be true.
    */
   fun satisfiesRequestedConstraint(requested: Constraint): ConstraintComparison {
      TODO("Need to implement satisfies() for constraint type ${this::class.simpleName}")
   }
}

data class ConstraintComparison(
   val satisfiesRequestedConstraint: Boolean,
   /**
    * When comparing a defined constraint to a requested constraint,
    * there are sometimes expressions provided in the constraint that
    * become substitutes for placeholders in the defined contract.
    *
    * eg:
    * operation filmsPublishedAfter(releaseDate:ReleaseDate):Film[](ReleaseDate > releaseDate)
    *
    * when evaluating if this contract can satisfy the consraint of Film[](ReleaseDate > 2019-02-01), then
    * we end up resolving the placeholder of "releaseDate" with a concrete expression.
    *
    * This list contains the pair of:
    *  - The expression as it was defined in the contract
    *  - The expression that satisfies it with a replaced value.
    */
   val providedValues:List<Pair<Expression,Expression>> = emptyList()
) {
   companion object {
      val DEFAULT_SATISFIED = ConstraintComparison(true, emptyList())
      val NOT_SATISFIED = ConstraintComparison(false, emptyList())
   }
   operator fun plus(other: ConstraintComparison): ConstraintComparison {
      if (!this.satisfiesRequestedConstraint || !other.satisfiesRequestedConstraint) {
         return NOT_SATISFIED
      }
      if (providedValues.isEmpty() && other.providedValues.isEmpty()) {
         return DEFAULT_SATISFIED
      }
      return ConstraintComparison(true, providedValues + other.providedValues)
   }
}
