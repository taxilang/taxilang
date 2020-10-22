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
         // ValueType being an Any could happen in the else branch of a when clause, if using
         // an accessor (such as column/jsonPath/xpath) , where we can't infer the value type returned.
         valueType.basePrimitive == PrimitiveType.ANY -> null
         receiverType.basePrimitive == PrimitiveType.ANY -> null
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
