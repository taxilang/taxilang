package lang.taxi.expressions

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.ImmutableEquality
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
   override val allReferencedTypes: Set<Type> = (inputs + this.returnType + expression.allReferencedTypes).toSet()
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

   private val equality = ImmutableEquality(
      this,
      LiteralExpression::returnType,
      LiteralExpression::value
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

// Pure tech-debt, resulting in how Accessors and Expressions have evolved.
data class FieldReferenceExpression(
   val selector: FieldReferenceSelector,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = selector.returnType
   val fieldName = selector.fieldName
}

data class TypeExpression(val type: Type, override val compilationUnits: List<CompilationUnit>) : Expression() {
   override val returnType: Type = type
   private val returnTypeName: QualifiedName = type.toQualifiedName()
   private val equality = ImmutableEquality(
      this,
      TypeExpression::returnTypeName
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

data class FunctionExpression(val function: FunctionAccessor, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   override val returnType: Type = function.returnType
   override val allReferencedTypes: Set<Type> = function.allReferencedTypes
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
   private val equality = ImmutableEquality(
      this,
      OperatorExpression::lhs,
      OperatorExpression::operator,
      OperatorExpression::rhs,
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()


   fun isMutuallyExclusive(other: OperatorExpression): Boolean {
      val myTypes = this.allReferencedTypes
      val theirTypes = other.allReferencedTypes

      // Short circut: If there is no overlap in types, they're not mutually exclusive
      if (myTypes.none { theirTypes.contains(it) }) {
         return false
      }
      // Short circut: If this is an or, then bail.  (This doesn't feel right - prove me wrong with tests)
      if (this.operator == FormulaOperator.LogicalOr || !this.operator.isLogicalOrComparisonOperator()) {
         return false
      }

      // Check for nested expressions:
      when {
         lhs is OperatorExpression && lhs.isMutuallyExclusive(other) -> return true
         rhs is OperatorExpression && rhs.isMutuallyExclusive(other) -> return true
         other.lhs is OperatorExpression && other.lhs.isMutuallyExclusive(this) -> return true
         other.rhs is OperatorExpression && other.rhs.isMutuallyExclusive(this) -> return true
      }


      val myOperator = this.operator
      val theirOperator = other.operator
      val allOperators = setOf(myOperator, theirOperator)
      val operatorsAreMutuallyExclusive = when {
         allOperators.containsAll(listOf(FormulaOperator.Equal, FormulaOperator.NotEqual)) -> true
         else -> false
      }
      val myExpressions = listOf(lhs,rhs)
      val theirExpressions = listOf(other.lhs,other.rhs)
      return when {
         myTypes == theirTypes && operatorsAreMutuallyExclusive -> true
         myExpressions.containsInstance<LiteralExpression>() && theirExpressions.containsInstance<LiteralExpression>() && allOperators == setOf(FormulaOperator.Equal) -> {
            // Here, these are two literal expressions (A == 'Foo') and (A == 'Foo')
            val myLiterals = myExpressions.filterIsInstance<LiteralExpression>().map { it.value }.toSet()
            val theirLiterals = theirExpressions.filterIsInstance<LiteralExpression>().map { it.value }.toSet()
            // These two statements cannot both be true if their expressions are for different values
            myLiterals != theirLiterals
         }
         // TODO : There are other conditions when these are mutually exclusive.  Need to work those out
         else -> false
      }
   }

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

   override val allReferencedTypes: Set<Type> =
      ((this.lhs.allReferencedTypes + this.rhs.allReferencedTypes) + this.returnType).toSet()


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

inline fun <reified R> Iterable<*>.containsInstance(): Boolean {
   return this.filterIsInstance<R>().isNotEmpty()
}
