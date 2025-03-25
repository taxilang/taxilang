package lang.taxi.services.operations.constraints

import lang.taxi.ImmutableEquality
import lang.taxi.expressions.Expression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.MemberAccessExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.CompilationUnit
import lang.taxi.types.FormulaOperator
import lang.taxi.types.MemberTypeReferenceExpression
import lang.taxi.utils.log

/**
 * A Simple wrapper around Expressions to make them implement the Constraint contract
 */
class ExpressionConstraint(val expression: Expression) : Constraint {
   private val equality = ImmutableEquality(this, ExpressionConstraint::expression)
   override fun hashCode(): Int = equality.hash()
   override fun equals(other: Any?) = equality.isEqualTo(other)

   override fun asTaxi(): String = expression.asTaxi()


   override fun satisfiesRequestedConstraint(requested: Constraint): ConstraintComparison {
      if (requested !is ExpressionConstraint) {
         return ConstraintComparison.NOT_SATISFIED
      }
      return satisfies(this.expression, requested.expression)
   }

   private fun satisfies(constraintToCheck: Expression, requestedConstraint: Expression): ConstraintComparison {
      return when {
         constraintToCheck is OperatorExpression && requestedConstraint is OperatorExpression -> satisfies(
            constraintToCheck,
            requestedConstraint
         )

         constraintToCheck is TypeExpression && requestedConstraint is TypeExpression -> satisfies(
            constraintToCheck,
            requestedConstraint
         )
         // This is typical - where a constraint in a contract is defined using variables,
         // but the requirement is using actual values.
         constraintToCheck is ArgumentSelector && requestedConstraint is LiteralExpression -> satisfies(
            constraintToCheck,
            requestedConstraint
         )

         constraintToCheck is ArgumentSelector && requestedConstraint is ArgumentSelector -> satisfies(
            constraintToCheck, requestedConstraint
         )
         constraintToCheck is ArgumentSelector && requestedConstraint is MemberTypeReferenceExpression -> satisfies(constraintToCheck, requestedConstraint)
         constraintToCheck is ArgumentSelector && requestedConstraint is MemberAccessExpression -> satisfies(constraintToCheck, requestedConstraint)


         else -> {
            log().warn("constraint satisfies check not implemented for comparison between ${constraintToCheck::class.simpleName} and ${requestedConstraint::class.simpleName}")
            ConstraintComparison.NOT_SATISFIED
         }
      }
   }

   private fun satisfies(
      constraintToCheck: OperatorExpression,
      requestedConstraint: OperatorExpression,
   ): ConstraintComparison {
      if (constraintToCheck.operator != requestedConstraint.operator) {
         // TODO : Is this always false?
         // Are there overlaps with things like GEQ vs GE?
         return ConstraintComparison.NOT_SATISFIED
      }

      // When evaluating an expression, the contract on the operation could be defined as
      // Foo( Name == someName), and the requested operation could be Foo(someName == Name).
      // With equal comparisons, these two should be considered the same.
      // But, comparing the expressions won't work unless we swap them around.
      val validToPassWithInvertedComparison = constraintToCheck.operator == FormulaOperator.Equal
      var usingInvertedComparison: Boolean = false
      val lhsSatisfied = satisfies(constraintToCheck.lhs, requestedConstraint.lhs).let { lhsSatisfied ->
         if (!lhsSatisfied.satisfiesRequestedConstraint) {
            // The contract wasn't satisfied as-is, but
            // check by swapping the parameters, if permitted (ie., if checking == )
            if (validToPassWithInvertedComparison) {
               usingInvertedComparison = true
               satisfies(constraintToCheck.rhs, requestedConstraint.lhs)
            } else {
               lhsSatisfied
            }
         } else {
            lhsSatisfied
         }
      }
      if (!lhsSatisfied.satisfiesRequestedConstraint) {
         return lhsSatisfied
      }
      val rhsSatisfied = if (usingInvertedComparison) {
         satisfies(constraintToCheck.lhs, requestedConstraint.rhs)
      } else {
         satisfies(constraintToCheck.rhs, requestedConstraint.rhs)
      }
      if (!rhsSatisfied.satisfiesRequestedConstraint) {
         return rhsSatisfied
      }
      return lhsSatisfied + rhsSatisfied
   }


   private fun satisfies(constraintToCheck: TypeExpression, requestedConstraint: TypeExpression): ConstraintComparison {
      // TODO : IsAssignableTo vs isAssignableFrom?
      return if (constraintToCheck.type.isAssignableTo(requestedConstraint.type)) {
         ConstraintComparison.DEFAULT_SATISFIED
      } else {
         ConstraintComparison.NOT_SATISFIED
      }
   }

   private fun satisfies(
      constraintToCheck: ArgumentSelector,
      requestedConstraint: LiteralExpression
   ): ConstraintComparison {
      return if (!requestedConstraint.returnType.isAssignableTo(constraintToCheck.returnType)) {
         ConstraintComparison.NOT_SATISFIED
      } else {
         ConstraintComparison(
              true,
            listOf(constraintToCheck to requestedConstraint)
         )
      }
   }

   private fun satisfies(
      constraintToCheck: ArgumentSelector,
      requestedConstraint: MemberTypeReferenceExpression
   ): ConstraintComparison {
      return if (!requestedConstraint.returnType.isAssignableTo(constraintToCheck.returnType)) {
         ConstraintComparison.NOT_SATISFIED
      } else {
         ConstraintComparison(
            true,
            listOf(constraintToCheck to requestedConstraint)
         )
      }
   }
   private fun satisfies(
      constraintToCheck: ArgumentSelector,
      requestedConstraint: MemberAccessExpression
   ): ConstraintComparison {
      return if (!requestedConstraint.returnType.isAssignableTo(constraintToCheck.returnType)) {
         ConstraintComparison.NOT_SATISFIED
      } else {
         ConstraintComparison(
            true,
            listOf(constraintToCheck to requestedConstraint)
         )
      }
   }
   private fun satisfies(
      constraintToCheck: ArgumentSelector,
      requestedConstraint: ArgumentSelector
   ): ConstraintComparison {
      return if (!requestedConstraint.returnType.isAssignableTo(constraintToCheck.returnType)) {
         ConstraintComparison.NOT_SATISFIED
      } else {
         ConstraintComparison(
            true,
            listOf(constraintToCheck to requestedConstraint)
         )
      }
   }


   override val compilationUnits: List<CompilationUnit> = expression.compilationUnits
}


