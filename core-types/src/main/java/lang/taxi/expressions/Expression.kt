package lang.taxi.expressions

import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.Accessor
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.FormulaOperator
import lang.taxi.types.LiteralAccessor
import lang.taxi.types.NumberTypes
import lang.taxi.types.PrimitiveType
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

// Note: Expression inheriting Accessor is tech debt,
// and really around the wrong way.
// An Accessor is an Expression, not vice versa.
// However, we build Accessors first, and then expressions.
// The only reason we specify that Expression inherits Accessor is because
// we want to specify that an Expression should declare a returnType
sealed class Expression : Compiled, TaxiStatementGenerator, Accessor {
   override fun asTaxi(): String {
      // TODO: Check this... probably not right
      return this.compilationUnits.first().source.content
   }
}

data class LambdaExpression(
   val inputs: List<Type>,
   val expression: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = expression.returnType
}

data class LiteralExpression(val literal: LiteralAccessor, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   override val returnType: Type = literal.returnType
}

data class TypeExpression(val type: Type, override val compilationUnits: List<CompilationUnit>) : Expression() {
   override val returnType: Type = type
}

data class FunctionExpression(val function: FunctionAccessor, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   override val returnType: Type = function.returnType
}

data class OperatorExpression(
   val lhs: Expression,
   val operator: FormulaOperator,
   val rhs: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type
      get() {
         if (operator.isLogicalOperator()) {
            return PrimitiveType.BOOLEAN
         }

         val lhsType = lhs.returnType.basePrimitive ?: return PrimitiveType.ANY
         val rhsType = rhs.returnType.basePrimitive ?: return PrimitiveType.ANY
         val types = setOf(lhsType, rhsType)

         // If all the types are numeric, then choose the highest precision
         // ie., Int - Double = Double
         if (types.all { PrimitiveType.NUMBER_TYPES.contains(it) }) {
            return NumberTypes.getTypeWithHightestPrecision(types)
         }

         // If there's only one type here, use that
         if (types.distinct().size == 1) {
            return types.distinct().single()
         }

         // Give up.  TODO : Other scenarios
         return PrimitiveType.ANY
      }

}

data class ExpressionGroup(val expressions: List<Expression>) : Expression() {
   override val compilationUnits: List<CompilationUnit> = expressions.flatMap { it.compilationUnits }
}

fun List<Expression>.toExpressionGroup(): Expression {
   return if (this.size == 1) {
      this.first()
   } else {
      ExpressionGroup(this)
   }
}
