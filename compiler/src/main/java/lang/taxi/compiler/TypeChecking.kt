package lang.taxi.compiler

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import org.antlr.v4.runtime.ParserRuleContext

object TypeChecking {
   fun assertIsAssignable(valueType: Type, receiverType: Type, token: ParserRuleContext): CompilationError? {
      // This is a first pass, pretty sure this is naieve.
      // Need to take the Vyne implmentation at Type.kt
      return when {
         // Basically, if either type are of Any, we disable type checking.
         // May want to refine this later, but there are challenges
         // around accessors with unknown/inferred types, such as column() / xpath() ,etc
         receiverType.basePrimitive == PrimitiveType.ANY -> null
         valueType.basePrimitive == PrimitiveType.ANY -> null
         receiverType.basePrimitive == valueType.basePrimitive -> null
         else -> CompilationError(token.start, "Type mismatch.  Found a type of ${valueType.qualifiedName} where a ${receiverType.qualifiedName} is expected")
      }
   }

   /**
    * Returns the value from the valueProvider if the valueType is assignable to the receiver type.
    * Otherwise, generates a Not Assignable compiler error
    */
   fun <A> ifAssignable(valueType: Type, receiverType: Type, token: ParserRuleContext, valueProvider: () -> A): Either<CompilationError, A> {
      val error = assertIsAssignable(valueType, receiverType, token)

      return if (error == null) {
         Either.right(valueProvider())
      } else {
         Either.left(error)
      }
   }
}
