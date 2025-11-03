package lang.taxi.expressions

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import lang.taxi.ImmutableEquality
import lang.taxi.accessors.Accessor
import lang.taxi.accessors.Argument
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.functions.CallableInvocationExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.services.Callable
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.services.ServiceMember
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.*
import kotlin.system.measureTimeMillis

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
   val inputs: List<Argument>,
   val expression: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = expression.returnType

   // type Adults by (age:Age) -> (Person[](Age > age)) -> Person[].first()
   // This doesn't feel right - we shouldn't be collapsing inputs, we should be
   // recursing expressions.
   val allInputs: List<Argument> = if (expression is LambdaExpression) {
      inputs + expression.allInputs
   } else {
      inputs
   }
}

/**
 * An expression that returns an object literal
 */
data class ObjectLiteralExpression(
   override val returnType: Type,
   val expressionMap: Map<String, Expression>,
   override val compilationUnits: List<CompilationUnit>
) : Literal, Expression() {
   override fun asTypedValue(): TypedValue {
      val values = this.expressionMap.map {
         if (it.value !is Literal) {
            error("This variable does not contain a constant")
         }
         val literal = it.value as Literal
         it.key to literal.asTypedValue().value
      }.toMap()
      return TypedValue(returnType, values)
   }
}

interface Literal {
   fun asTypedValue(): TypedValue
}

data class LiteralArray(
   override val returnType: Type,
   val members: List<Expression>,
   override val compilationUnits: List<CompilationUnit>
) : Literal, Expression() {
   private val equality = ImmutableEquality(this, LiteralArray::returnType, LiteralArray::members)
   override fun hashCode(): Int = equality.hash()
   override fun asTypedValue(): TypedValue {
      return TypedValue(returnType, toListOfValues())
   }

   override fun equals(other: Any?): Boolean = equality.isEqualTo(other)

   /**
    * Provides backwards compatibility where old code was relying on a list of values.
    * Ideally, code should migrate to use a LiteralArray containing a colletion of expressions.
    */
   fun toListOfValues(): List<Any?> {
      return members.map { expression ->
         require(expression is Literal) { "Cannot convert LiteralArray to list of values, as found member that was not a literal: ${expression::class.simpleName}" }
         expression.asTypedValue().value
      }
   }

   val memberType = Arrays.unwrapPossibleArrayType(returnType)
}

// Decorator around a LiteralAccessor to make it an Expression.
// Pure tech-debt, resulting in how Accessors and Expressions have evolved.
data class LiteralExpression(val literal: LiteralAccessor, override val compilationUnits: List<CompilationUnit>) :
   Literal, Expression() {
   companion object {
      fun isNullExpression(expression: Expression): Boolean {
         return expression is LiteralExpression && LiteralAccessor.isNullLiteral(expression.literal)
      }
   }

   override fun asTaxi(): String {
      return literal.asTaxi()
   }

   private val equality = ImmutableEquality(this, LiteralExpression::literal)
   override fun hashCode(): Int = equality.hash()
   override fun equals(other: Any?): Boolean = equality.isEqualTo(other)

   override val returnType: Type = literal.returnType

   val value = literal.value

   override fun asTypedValue(): TypedValue {
      val value = if (this.value is Map<*, *>) {
         this.value.mapValues { (_, v) ->
            when (v) {
               null -> v
               is Literal -> v.asTypedValue().value
               else -> error("can't convert value with type ${v::class.simpleName} to TypedValue")
            }
         }
      } else {
         this.value
      }
      return TypedValue(this.returnType, value)
   }
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

/**
 * Encapsulates traversing a member of a property
 * The result of the LHS is the call target, and the RHS is the expression
 * to evaluate against it.
 *
 * I suspect this is the replacement of FieldReferenceExpression, but not sure yet.
 */
data class MemberAccessExpression(
   val lhs: Expression,
   val rhs: FieldReferenceSelector,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = rhs.returnType
}

data class TypeExpression(
   val type: Type,
   val constraints: List<Constraint>,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = type
}

data class ServiceExpression(val service: Service, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   override val returnType: Type = PrimitiveType.VOID
}

/**
 * Instructs the runtime to cast the provided expression to the type
 */
data class CastExpression(
   val type: Type,
   val expression: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = type
}

data class NegatedExpression(val expression: Expression, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   override val returnType: Type = expression.returnType
}

data class FunctionExpression(val function: FunctionAccessor, override val compilationUnits: List<CompilationUnit>) :
   Expression() {
   override val returnType: Type = function.returnType

   val inputs = function.inputs
}

data class OperationInvocationExpression(
   val service: Service,
   val member: ServiceMember,
   override val inputs: List<Accessor>,
   override val compilationUnits: List<CompilationUnit>
) :
   Expression(), CallableInvocationExpression {
   override val returnType: Type = member.returnType
   override val callable: Callable = member
}

data class ExtensionFunctionExpression(
   val functionExpression: FunctionExpression,
   val receiverValue: Expression,
   override val compilationUnits: List<CompilationUnit>
) : Expression() {
   override val returnType: Type = functionExpression.returnType
   val inputs = functionExpression.inputs

   /**
    * Indicates if the receiver function is looking for Type<T>, rather than an instance of T
    */
   val receiverIsTypeReference: Boolean
      get() {
         return functionExpression.function.parameters[0].type is TypeReference
      }
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
      OperatorExpression::rhs
   )

   override fun hashCode(): Int = equality.hash()
   override fun equals(other: Any?): Boolean = equality.isEqualTo(other)

   override fun asTaxi(): String {
      return "${lhs.asTaxi()} ${operator.symbol} ${rhs.asTaxi()}"
   }

   companion object {
//      fun getReturnType(lhsType: PrimitiveType, operator: FormulaOperator, rhsType: PrimitiveType): Type? {
//         val typeOrError = TypeResolver.getCommonType(lhsType, operator, rhsType)
//         return when  (typeOrError) {
//            is Either.Right -> typeOrError.value
//            else -> TODO("Error handling when type coercion fails")
//         }
//         if (operator.isLogicalOrComparisonOperator()) {
//            return PrimitiveType.BOOLEAN
//         }
//         val types = setOf(lhsType, rhsType)
//
//         // If all the types are numeric, then choose the highest precision
//         // unless we're dividing...
//         // ie., Int - Double = Double
//         if (types.all { PrimitiveType.NUMBER_TYPES.contains(it) }) {
//            return if (operator == FormulaOperator.Divide) {
//               when {
//                  types.contains(PrimitiveType.DECIMAL) -> PrimitiveType.DECIMAL
//                  types.contains(PrimitiveType.DOUBLE) -> PrimitiveType.DOUBLE
//                  else -> PrimitiveType.DECIMAL // Int / Int = Decimal
//               }
//            } else {
//               NumberTypes.getTypeWithHighestPrecision(types)
//            }
//         }
//
//         // If there's only one type here, use that
//         if (types.distinct().size == 1) {
//            return types.distinct().single()
//         }
//         // special cases
//         if (types == setOf(PrimitiveType.TIME, PrimitiveType.LOCAL_DATE)) {
//            return PrimitiveType.INSTANT
//         }
//
//         // Give up.  TODO : Other scenarios
//         return null
//      }
   }

   override val strictReturnType: Either<String, Type> =
      TypeResolver.getCommonType(lhs.returnType, operator, rhs.returnType)

   //      get() {
//         val lhsType = lhs.returnType.basePrimitive ?: PrimitiveType.ANY
//         val rhsType = rhs.returnType.basePrimitive ?: PrimitiveType.ANY
//         return getReturnType(
//            lhsType = lhsType,
//            operator = operator,
//            rhsType = rhsType
//         )?.right()
//            ?: "Unable to determine the return type resulting from ${lhsType.name} ${operator.symbol} ${rhsType.name}".left()
//      }
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

   override val returnType: Type = projection.returnType
}

fun List<Expression>.toExpressionGroup(): Expression {
   return if (this.size == 1) {
      this.first()
   } else {
      ExpressionGroup(this)
   }
}
