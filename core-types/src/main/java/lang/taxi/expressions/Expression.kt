package lang.taxi.expressions

import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.FormulaOperator
import lang.taxi.types.LiteralAccessor
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

sealed class Expression : Compiled, TaxiStatementGenerator {
   override fun asTaxi(): String {
      // TODO: Check this... probably not right
      return this.compilationUnits.first().source.content
   }
}

data class LambdaExpression(val inputs:List<Type>, val expression:Expression, override val compilationUnits: List<CompilationUnit>) : Expression()
data class LiteralExpression(val literal:LiteralAccessor, override val compilationUnits: List<CompilationUnit>) : Expression()
data class TypeExpression(val type: Type, override val compilationUnits: List<CompilationUnit>) : Expression()
data class FunctionExpression(val function: FunctionAccessor, override val compilationUnits: List<CompilationUnit>) :
   Expression()

data class OperatorExpression(
   val lhs: Expression,
   val operator: FormulaOperator,
   val rhs: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression()

data class ExpressionGroup(val expressions:List<Expression>) : Expression() {
   override val compilationUnits: List<CompilationUnit> = expressions.flatMap { it.compilationUnits }
}

fun List<Expression>.toExpressionGroup():Expression {
   return if (this.size == 1) {
      this.first()
   } else {
      ExpressionGroup(this)
   }
}
