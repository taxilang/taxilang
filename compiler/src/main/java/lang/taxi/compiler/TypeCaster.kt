package lang.taxi.compiler

import arrow.core.Either
import arrow.core.right
import lang.taxi.expressions.Expression
import lang.taxi.expressions.LiteralExpression
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type

object TypeCaster {
   /**
    * When a pair of expressions are performing comparisons, and one of them is literal
    * we might need to coerce the literal to the correct type to make an expression parsable.
    * eg: When comparing a date - the date literal will be provided as a string, and we need to
    * parse
    */
   fun coerceTypesIfRequired(lhs: Expression, rhs: Expression): Either<String, Pair<Expression, Expression>> {
      return when {
         // Don't try to coerce two literals
         lhs is LiteralExpression && rhs is LiteralExpression -> (lhs to rhs).right()
         lhs is LiteralExpression && rhs !is LiteralExpression -> {
            coerceLiteralToType(literalExpression = lhs, rhs.returnType).map { it to rhs }
         }

         lhs !is LiteralExpression && rhs is LiteralExpression -> {
            coerceLiteralToType(rhs, lhs.returnType).map { lhs to it }
         }

         else -> (lhs to rhs).right()
      }

   }

   private fun coerceLiteralToType(
      literalExpression: LiteralExpression,
      returnType: Type
   ): Either<String, LiteralExpression> {
      val literalBaseType = literalExpression.returnType.basePrimitive ?: PrimitiveType.ANY
      val expectedReturnType = returnType.basePrimitive ?: PrimitiveType.ANY
      if (literalBaseType == expectedReturnType) {
         return literalExpression.right()
      }
      if (literalBaseType == PrimitiveType.ANY || expectedReturnType == PrimitiveType.ANY) {
         // we can't do anything here.
         return literalExpression.right()
      }
      return if (expectedReturnType.canCoerce(literalExpression.value)) {
         expectedReturnType.coerce(literalExpression.value).map { coercedValue ->
            literalExpression.copy(
               literalExpression.literal.copy(
                  value = coercedValue,
                  returnType = expectedReturnType
               )
            )
         }
      } else {
         literalExpression.right()
      }
   }
}
