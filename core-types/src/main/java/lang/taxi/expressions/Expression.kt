package lang.taxi.expressions

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.*

// Note: Expression inheriting Accessor is tech debt,
// and really around the wrong way.
// An Accessor is an Expression, not vice versa.
// However, we build Accessors first, and then expressions.
// The only reason we specify that Expression inherits Accessor is because
// we want to specify that an Expression should declare a returnType
abstract class Expression : Compiled, TaxiStatementGenerator, Accessor {
   override fun asTaxi(): String {
      // sanitize, stripping namespace declarations
      val raw = this.compilationUnits.first().source.content
      val taxi = if (raw.startsWith("namespace")) {
         raw.substring(raw.indexOf("{")).removeSurrounding("{", "}").trim()
      } else raw
      // TODO: Check this... probably not right
      return taxi
   }
}

data class LambdaExpression(
   val inputs: List<Type>,
   val expression: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = expression.returnType
}


// Decorator around a LiteralAccessor to make it an Expression.
// Pure tech-debt, resulting in how Accessors and Expressions have evolved.
data class LiteralExpression(val literal: LiteralAccessor, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   companion object {
      fun isNullExpression(expression: Expression): Boolean {
         return expression is LiteralExpression && LiteralAccessor.isNullLiteral(expression.literal)
      }
   }

   override val returnType: Type = literal.returnType

   val value = literal.value
}

// Pure tech-debt, resulting in how Accessors and Expressions have evolved.
data class FieldReferenceExpression(
   val selectors: List<FieldReferenceSelector>,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = selectors.last().returnType

   val fieldNames: List<String>
      get() {
         return selectors.map { it.fieldName }
      }

   val path: String
      get() {
         return selectors.joinToString(".") { it.fieldName }
      }

}

data class TypeExpression(val type: Type, override val compilationUnits: List<CompilationUnit>) : Expression() {
   override val returnType: Type = type
}

data class FunctionExpression(val function: FunctionAccessor, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   override val returnType: Type = function.returnType

   val inputs = function.inputs
}

/**
 * An OperatorExpression is a tuple of
 * Lhs Operator Rhs
 *
 * The Lhs and Rhs can in turn be other expressions, allowing building of arbitarily complex
 * statements
 */
data class OperatorExpression(
   val lhs: Expression,
   val operator: FormulaOperator,
   val rhs: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   companion object {
      fun getReturnType(lhsType: PrimitiveType, operator: FormulaOperator, rhsType: PrimitiveType): Type? {
         if (operator.isLogicalOrComparisonOperator()) {
            return PrimitiveType.BOOLEAN
         }
         val types = setOf(lhsType, rhsType)

         // If all the types are numeric, then choose the highest precision
         // unless we're dividing...
         // ie., Int - Double = Double
         if (types.all { PrimitiveType.NUMBER_TYPES.contains(it) }) {
            return if (operator == FormulaOperator.Divide) {
               when {
                  types.contains(PrimitiveType.DECIMAL) -> PrimitiveType.DECIMAL
                  types.contains(PrimitiveType.DOUBLE) -> PrimitiveType.DOUBLE
                  else -> PrimitiveType.DECIMAL // Int / Int = Decimal
               }
            } else {
               NumberTypes.getTypeWithHightestPrecision(types)
            }
         }

         // If there's only one type here, use that
         if (types.distinct().size == 1) {
            return types.distinct().single()
         }
         // special cases
         if (types == setOf(PrimitiveType.TIME, PrimitiveType.LOCAL_DATE)) {
            return PrimitiveType.INSTANT
         }

         // Give up.  TODO : Other scenarios
         return null
      }
   }

   override val strictReturnType: Either<String, Type>
      get() {
         val lhsType = lhs.returnType.basePrimitive ?: PrimitiveType.ANY
         val rhsType = rhs.returnType.basePrimitive ?: PrimitiveType.ANY
         return getReturnType(
            lhsType = lhsType,
            operator = operator,
            rhsType = rhsType
         )?.right()
            ?: "Unable to determine the return type resulting from ${lhsType.name} ${operator.symbol} ${rhsType.name}".left()
      }
   override val returnType: Type = strictReturnType.getOrElse { PrimitiveType.ANY }

}

data class ExpressionGroup(val expressions: List<Expression>) : Expression() {
   override val compilationUnits: List<CompilationUnit> = expressions.flatMap { it.compilationUnits }
}

/**
 * A projecting expression is an expression that also contains a projection.
 * eg:
 * foo(a,b) as { someField : SomeType }
 *
 */
data class ProjectingExpression(val expression: Expression, val projection: FieldProjection) : Expression() {
   override val compilationUnits: List<CompilationUnit> = expression.compilationUnits

   override val returnType: Type
      get() = projection.projectedType
}

fun List<Expression>.toExpressionGroup(): Expression {
   return if (this.size == 1) {
      this.first()
   } else {
      ExpressionGroup(this)
   }
}
