package lang.taxi.compiler.fields

import lang.taxi.accessors.Accessor
import lang.taxi.expressions.Expression
import lang.taxi.expressions.TypeExpression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.query.DiscoveryType
import lang.taxi.types.ArrayType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.utils.log


/**
 * When compiling a field, it's type declaration may be one of:
 *  - A type
 *  - An expression (which infers the type)
 *  - A function (which infers the type)
 *
 *  We extract this to a seperate type as when we're parsing the type, we may additionally have
 *  to parse the expression / function statement too, and need to capture that in order to advise the underlying type.
 */
data class FieldTypeSpec private constructor(
   val type: Type,
   val function: FunctionAccessor?,
   val expression: Expression?
) {
   companion object {
      fun forType(type: Type) = FieldTypeSpec(type, null, null)
      fun forFunction(function: FunctionAccessor) = FieldTypeSpec(function.returnType, function, null)
      fun forExpression(expression: Expression) = FieldTypeSpec(expression.returnType, null, expression)
      fun forDiscoveryTypes(discoveryType: DiscoveryType): FieldTypeSpec {
         return when(val expression = discoveryType.expression) {
            // This is for backwards compatibility. It may be that we actually want to use the expression in
            // all cases.
            is TypeExpression -> forType(expression.type)
            else -> forExpression(expression)
         }
      }
   }

   val accessor: Accessor?

   /**
    * Returns the type that will actually be projected over when
    * using this field in an inline projection.
    *
    * (eg:
    * find { Movie } as {
    *   cast : Actor[] as {  //<---- the projected type here is Actor, not Actor[].
    *      foo : Bar
    *   }[]
    */
   val projectionType: Type = ArrayType.memberTypeIfArray(type)

   init {
      if (function != null && expression != null) {
         error("It is invalid to pass both a function and expression. Don't see how this could happen")
      }
      accessor = when {
         expression != null -> expression
         function != null -> function
         else -> null
      }

   }

}
