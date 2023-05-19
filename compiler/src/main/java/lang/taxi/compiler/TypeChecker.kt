package lang.taxi.compiler

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.messages.Severity
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.TypeChecker
import org.antlr.v4.runtime.ParserRuleContext

// These are TypeChecker extensions that require access to compiler specific concepts, such as compiler errors

fun TypeChecker.assertIsAssignable(valueType: Type, receiverType: Type, token: ParserRuleContext): CompilationError? {
   // This is a first pass, pretty sure this is naieve.
   // Need to take the Vyne implmentation at Type.kt
   return when {
      // ValueType being an Any could happen in the else branch of a when clause, if using
      // an accessor (such as column/jsonPath/xpath) , where we can't infer the value type returned.
      valueType.basePrimitive == PrimitiveType.ANY -> null
      receiverType.basePrimitive == PrimitiveType.ANY -> null
      valueType.isAssignableTo(receiverType) -> null
//         receiverType.basePrimitive == valueType.basePrimitive -> null
      else -> {
         val errorMessage =
            "Type mismatch. Type of ${valueType.qualifiedName} is not assignable to type ${receiverType.qualifiedName}"
         when (enabled) {
            FeatureToggle.DISABLED -> null
            FeatureToggle.ENABLED -> CompilationError(token.start, errorMessage)
            FeatureToggle.SOFT_ENABLED -> CompilationError(token.start, errorMessage, severity = Severity.WARNING)
         }
      }
   }
}

/**
 * Returns the value from the valueProvider if the valueType is assignable to the receiver type.
 * Otherwise, generates a Not Assignable compiler error
 */
fun <A> TypeChecker.ifAssignable(
   valueType: Type,
   receiverType: Type,
   token: ParserRuleContext,
   valueProvider: () -> A
): Either<CompilationError, A> {
   val error = assertIsAssignable(valueType, receiverType, token)

   return error?.left() ?: valueProvider().right()
}
