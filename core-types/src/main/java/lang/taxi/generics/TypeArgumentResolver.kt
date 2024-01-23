package lang.taxi.generics

import arrow.core.getOrHandle
import lang.taxi.accessors.Accessor
import lang.taxi.expressions.LambdaExpression
import lang.taxi.services.Parameter
import lang.taxi.types.*

/**
 * Respnosible for evaluating a generic expression (such as a lambda or function signature)
 * which contains type arguments, and resolving those arguments using the inputs provided.
 */
object TypeArgumentResolver {
   fun resolve(
      typeArguments: List<TypeArgument>,
      declaredInputs: List<Type>,
      providedInputTypes: List<Accessor>
   ): Map<TypeArgument, Type> {
      val resolvedTypes = typeArguments.map { typeArgument ->
         val resolvedType = resolveTypeArgumentFromInputAccessors(typeArgument, declaredInputs, providedInputTypes)
            ?: resolveTypeArgumentFromLambdaExpressionReturnTypes(typeArgument, declaredInputs, providedInputTypes)
            // It could be that we need more sophisticated resolution logic here - ie., interference from return types
            // etc.
            ?: error("Unable to resolve typeArgument ${typeArgument.declaredName}")
         typeArgument to resolvedType
      }
      return resolvedTypes.toMap()
   }

   /**
    * Checks to see if the type argument exists as the return type from an input
    * which is an expression (ie., a Lambda as an input),
    * and if so- looks at the expression to resolve the type
    */
   private fun resolveTypeArgumentFromLambdaExpressionReturnTypes(
      typeArgument: TypeArgument,
      declaredInputs: List<Type>,
      providedInputTypes: List<Accessor>
   ): Type? {
      return declaredInputs
         .asSequence()
         .mapIndexed { index, input ->
            if (input is LambdaExpressionType && input.returnType.qualifiedName == typeArgument.qualifiedName) {
                   val inputTypeAtPosition = providedInputTypes[index]
                   inputTypeAtPosition.returnType
            } else {
                   null
                }
            }
         .filterNotNull()
         .firstOrNull()
   }

   /**
    * Looks at the declaredInputs (as defined in a function signature),
    * and compares against the provided inputs
    */
   private fun resolveTypeArgumentFromInputAccessors(
      typeArgument: TypeArgument,
      declaredInputs: List<Type>,
      providedInputs: List<Accessor>
   ): Type? {
      require(declaredInputs.size == providedInputs.size) { "Required ${declaredInputs.size} parameters, only ${providedInputs.size} were provided" }
      val resolvedTypeParameter: Type? = declaredInputs
         .asSequence()
         .mapIndexed { index, declaredInput ->
            val providedInputAccessor = providedInputs[index]
            when (providedInputAccessor) {
               is LambdaExpression -> {
                  require(declaredInput is LambdaExpressionType) { "Expected parameter at index $index to be a lambda based on the values passed, but was ${declaredInput::class.simpleName}" }
                  attemptToResolveTypeArgumentFromLambdaExpression(typeArgument, declaredInput, providedInputAccessor)
               }
               else -> resolveTypeArgumentFromInputTypes(
                  typeArgument,
                  listOf(declaredInput),
                  listOf(providedInputAccessor.returnType)
               )
            }
         }
         .filterNotNull()
         .firstOrNull()
      return resolvedTypeParameter
   }

   /**
    * If one of the provided inputs is a lambda, look into the arguments within to attempt to resolve
    * type parameters.
    *
    * eg:
    * declare function <T,A> reduce(T[], (T,A) -> A):A
    *
    * Will look at the `(T,A) -> A` part of the above
    */
   private fun attemptToResolveTypeArgumentFromLambdaExpression(
      typeArgument: TypeArgument,
      declaredInput: LambdaExpressionType,
      providedInputAccessor: LambdaExpression
   ): Type? {
      val lambdaParameterTypes = declaredInput.parameterTypes
      val expressionParameterTypes = providedInputAccessor.inputs
      val result = resolveTypeArgumentFromInputTypes(typeArgument, lambdaParameterTypes, expressionParameterTypes)
      return result
   }

   private fun resolveTypeArgumentFromInputTypes(
      typeArgument: TypeArgument,
      declaredInputs: List<Type>,
      providedInputTypes: List<Type>
   ): Type? {
      require(declaredInputs.size == providedInputTypes.size) { "Expected that declaredInputs and providedInputs would have the same number of elements" }
      val resolvedTypeParameter: Type? = declaredInputs
         .asSequence()
         .mapIndexed { index, declaredInput ->
            val providedInput = providedInputTypes[index]
            if (declaredInput.typeParameters().isNotEmpty()) {
               // Reflection is "Special", as when we're resolving the type, we're not looking into the type parameters
               // of something else, we're looking at the type itself.
               if (TypeReference.isTypeReferenceTypeName(declaredInput.qualifiedName)) {
                  providedInput
               } else {
                  // For the "normal" case, look at the type parameters on the input
                  // we've been given, and resolve from there.
                  require(declaredInput.typeParameters().size == providedInput.typeParameters().size) {
                     "Not enough information to infer type argument ${typeArgument.declaredName} (of ${declaredInput.toQualifiedName().parameterizedName}) from the provided inputs"
                  }
                  resolveTypeArgumentFromInputTypes(
                     typeArgument,
                     declaredInput.typeParameters(),
                     providedInput.typeParameters()
                  )
               }
            } else if (typeArgument.qualifiedName == declaredInput.qualifiedName) {
               providedInput
            } else {
               null
            }
         }
         .filterNotNull()
         .firstOrNull()
      return resolvedTypeParameter
   }


   fun replaceTypeArguments(parameters: List<Parameter>, resolvedArguments: Map<TypeArgument, Type>): List<Parameter> {
      return parameters.map { parameter ->
         val type = replaceType(parameter.type, resolvedArguments)
         if (type != parameter.type) {
            parameter.copy(type = type)
         } else {
            parameter
         }
      }
   }
   fun replaceType(type: Type, resolvedArguments: Map<TypeArgument, Type>): Type {
      return when {
         type is TypeArgument -> resolvedArguments[type] ?: error("No resolution possible for type ${type.declaredName}")
         type is GenericType -> {
            val replacedTypeParameters = type.typeParameters().map { replaceType(it, resolvedArguments) }
            type.withParameters(replacedTypeParameters).getOrHandle { throw RuntimeException(it.message) }
         }
         type is LambdaExpressionType -> {
            val replacedReturnType = replaceType(type.returnType, resolvedArguments)
            val replacedParameterTypes = type.parameterTypes.map { replaceType(it, resolvedArguments) }
            val replaced = type.copy(parameterTypes = replacedParameterTypes, returnType = replacedReturnType)
            replaced
         }

         else -> type
      }
   }

   fun declarationCanResolveArgument(typeToInspect: Type, typeArgumentToResolve: Type): Boolean {
      return when {
         typeArgumentToResolve !is TypeArgument -> false
         // eg: declare function <T> foo(T):T
         typeToInspect is TypeReference && typeToInspect.type == typeArgumentToResolve -> true
         // eg: declare function <T> foo(T[]):T
         typeToInspect.typeParameters().any { declarationCanResolveArgument(it, typeArgumentToResolve) } -> true
         // eg: when introspecting the T in Array<T>
         typeToInspect is TypeArgument && typeToInspect.qualifiedName == typeArgumentToResolve.qualifiedName -> true
         else -> false
      }

   }
}

// We don't have access to CompilationError here, so throw this instead.
class TypeResolutionFailedException(messages: List<String>) : RuntimeException(messages.joinToString("\n"))
