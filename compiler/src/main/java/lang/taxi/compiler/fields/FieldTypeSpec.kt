package lang.taxi.compiler.fields

import lang.taxi.accessors.Accessor
import lang.taxi.expressions.Expression
import lang.taxi.functions.FunctionAccessor
import lang.taxi.query.DiscoveryType
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
      fun forDiscoveryTypes(typesToDiscover: List<DiscoveryType>): FieldTypeSpec {
         return when {
            typesToDiscover.isEmpty() -> TODO("How do we derive FieldTypeSpec without any inputs?")
            typesToDiscover.size == 1 -> forType(typesToDiscover.single().type)
            else -> {
               log().warn("Multiple discovery types for projection results are not yet supported - returning Any")
               forType(PrimitiveType.ANY)
            }
         }
      }
   }
   val accessor:Accessor?

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
