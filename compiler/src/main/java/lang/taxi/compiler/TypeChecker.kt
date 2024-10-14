package lang.taxi.compiler

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Errors
import lang.taxi.messages.Severity
import lang.taxi.toCompilationUnit
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.Arrays
import lang.taxi.types.PrimitiveType
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeChecker
import org.antlr.v4.runtime.ParserRuleContext

// These are TypeChecker extensions that require access to compiler specific concepts, such as compiler errors

fun TypeChecker.assertIsAssignable(valueType: Type, receiverType: Type, token: ParserRuleContext): CompilationError? {
   // This is a first pass, pretty sure this is naieve.
   // Need to take the Vyne implmentation at Type.kt
   fun error(): CompilationError? {
      return when (enabled) {
         FeatureToggle.DISABLED -> null
         FeatureToggle.ENABLED -> Errors.typeMismatch(valueType, receiverType, token)
         FeatureToggle.SOFT_ENABLED -> Errors.typeMismatch(valueType, receiverType, token).copy(severity = Severity.WARNING)
      }
   }

   return when {
      valueType.isAssignableTo(receiverType) -> null
//      valueType.isScalar != receiverType.isScalar -> error()
//      Arrays.isArray(valueType) != Arrays.isArray(receiverType) -> error()
      // ValueType being an Any could happen in the else branch of a when clause, if using
      // an accessor (such as column/jsonPath/xpath) , where we can't infer the value type returned.
      valueType.basePrimitive == PrimitiveType.ANY -> null
      receiverType.basePrimitive == PrimitiveType.ANY -> null

//         receiverType.basePrimitive == valueType.basePrimitive -> null
      else -> error()
   }
}

fun TypeChecker.assertIsProjectable(sourceType: Type, targetType: Type, token: ParserRuleContext):CompilationError? {
   return when {
      Arrays.isArray(sourceType) && !Arrays.isArray(targetType) && targetType.anonymous -> "Cannot project an array to a non-array. Try adding [] after your projection type definition"
      Arrays.isArray(sourceType) && !Arrays.isArray(targetType) && !targetType.anonymous -> "Cannot project an array to a non-array. Did you mean ${targetType.toQualifiedName().typeName}[] ?"
      !Arrays.isArray(sourceType) && Arrays.isArray(targetType) -> "Cannot project an object to an array."
      StreamType.isStream(sourceType) && !Arrays.isArray(targetType) && targetType.anonymous -> "A stream must be projected to an array. Try adding [] after your projection type definition"
      StreamType.isStream(sourceType) && !Arrays.isArray(targetType) && !targetType.anonymous -> "A stream must be projected to an array. Did you mean ${targetType.toQualifiedName().typeName}[] ?"
      else -> null
   }?.let { message ->
      CompilationError(token.toCompilationUnit(), message)
   }
}

/**
 * Returns the value from the valueProvider if the valueType is assignable to the receiver type.
 * Otherwise, generates a Not Assignable compiler error
 */
fun <A> TypeChecker.ifAssignable(
   valueType: Type,
   receiverType: Type?,
   token: ParserRuleContext,
   valueProvider: () -> A
): Either<CompilationError, A> {
   if (receiverType == null) {
      return valueProvider().right()
   }
   val error = assertIsAssignable(valueType, receiverType, token)

   return error?.left() ?: valueProvider().right()
}

/**
 * Returns the value from the valueProvider if the valueType is assignable to the receiver type.
 * Otherwise, generates a Not Assignable compiler error
 */
fun <A> TypeChecker.ifAssignableOrErrorList(
   valueType: Type,
   receiverType: Type?,
   token: ParserRuleContext,
   valueProvider: () -> A
): Either<List<CompilationError>, A> {
   return ifAssignable(valueType, receiverType, token, valueProvider)
      .mapLeft { listOf(it) }
}

/**
 * Returns the value from the valueProvider if the valueType is assignable to the receiver type.
 * Otherwise, generates a Not Assignable compiler error
 */
fun <A> TypeChecker.ifProjectableOrErrorList(
   sourceType: Type,
   projectedType: Type,
   token: ParserRuleContext,
   valueProvider: () -> A
): Either<List<CompilationError>, A> {
   val error = assertIsProjectable(sourceType,projectedType, token)
   return if(error != null) {
      listOf(error).left()
   } else {
      valueProvider().right()
   }
}

