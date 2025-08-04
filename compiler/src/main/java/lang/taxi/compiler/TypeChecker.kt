package lang.taxi.compiler

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Errors
import lang.taxi.accessors.Accessor
import lang.taxi.expressions.LambdaExpression
import lang.taxi.expressions.ProjectingExpression
import lang.taxi.messages.Severity
import lang.taxi.services.Parameter
import lang.taxi.toCompilationUnit
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.Arrays
import lang.taxi.types.LambdaExpressionType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeChecker
import lang.taxi.utils.createCompilationError
import org.antlr.v4.runtime.ParserRuleContext


fun TypeChecker.assertIsAssignable(
   inputAccessor: Accessor,
   parameter: Parameter,
   context: ParserRuleContext
): CompilationError? {
   // TODO  :This could get out of hand.
   // Should we move this logic onto the the parameter / accessor with a .isAssignable() type
   // check there?
   if (parameter.type is LambdaExpressionType) {
      when {
         inputAccessor is LambdaExpression -> {} // do nothing
         inputAccessor is ProjectingExpression && inputAccessor.expression is LambdaExpression -> {} // do nothing
         else -> return CompilationError(context.toCompilationUnit(), "Expected a lambda expression here")
      }
   }
   return assertIsAssignable(inputAccessor.returnType, parameter.type ?: PrimitiveType.ANY, context)
}
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

   val valueIsArray = Arrays.isArray(valueType)
   val receiverIsArray = Arrays.isArray(receiverType)
   return when {
      valueType.isAssignableTo(receiverType) -> null
      // I *want* to use this check - which is the right check
      // however, we have a nasty condition where, when compiling an expression type
      // we have to use an interim type definition whilst we compile the expression itself.
      // In that scenario, this check fails.
      // There's definitely a solution to this, but not one I've found.
      // Tests fail when this is uncommented.
//      valueType.isScalar != receiverType.isScalar -> error()
      Arrays.isArray(valueType) != Arrays.isArray(receiverType) -> error()
      Arrays.isArray(receiverType) != Arrays.isArray(valueType) -> error()
      // ValueType being an Any could happen in the else branch of a when clause, if using
      // an accessor (such as column/jsonPath/xpath) , where we can't infer the value type returned.
      !valueIsArray && valueType.basePrimitive == PrimitiveType.ANY -> null
      !receiverIsArray && receiverType.basePrimitive == PrimitiveType.ANY -> null

//         receiverType.basePrimitive == valueType.basePrimitive -> null
      else -> error()
   }
}

fun TypeChecker.assertIsProjectable(sourceType: Type, targetType: Type, token: ParserRuleContext):CompilationError? {
   return when {

      // Projecting Array to non-array is now permitted - this is required to allow
      // field-based aggregation of data
//      Arrays.isArray(sourceType) && !Arrays.isArray(targetType) && targetType.anonymous -> "Cannot project an array to a non-array. Try adding [] after your projection type definition"
//      Arrays.isArray(sourceType) && !Arrays.isArray(targetType) && !targetType.anonymous -> "Cannot project an array to a non-array. Did you mean ${targetType.toQualifiedName().typeName}[] ?"

      // Projecting a non-array to an array is now permitted - this is required
      // to allow field selection of an array from an object
      //      !Arrays.isArray(sourceType) && Arrays.isArray(targetType) -> "Cannot project an object to an array."

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

fun <A> TypeChecker.ifAssignable(
   inputAccessor: Accessor,
   parameter: Parameter,
   context: ParserRuleContext,
   valueProvider: () -> A
): Either<CompilationError, A> {
   val error = assertIsAssignable(inputAccessor, parameter, context)
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
fun <A> TypeChecker.ifAssignableOrErrorList(
   valueAccessor: Accessor,
   parameter: Parameter,
   token: ParserRuleContext,
   valueProvider: () -> A
): Either<List<CompilationError>, A> {
   return ifAssignable(valueAccessor, parameter, token, valueProvider)
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

