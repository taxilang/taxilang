package lang.taxi.generics

import arrow.core.getOrHandle
import lang.taxi.expressions.LambdaExpression
import lang.taxi.services.Parameter
import lang.taxi.types.Accessor
import lang.taxi.types.GenericType
import lang.taxi.types.LambdaExpressionType
import lang.taxi.types.Type
import lang.taxi.types.TypeArgument

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
         val resolvedType = resolveTypeArgumentFromInputAccessors(typeArgument, declaredInputs, providedInputTypes) ?:
         // It could be that we need more sophisticated resolution logic here - ie., interference from return types
         // etc.
         error("Unable to resolve typeArgument ${typeArgument.declaredName}")
         typeArgument to resolvedType
      }
      return resolvedTypes.toMap()
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
      require(declaredInputs.size == providedInputs.size) { "Expected that declaredInputs and providedInputs would have the same number of elements" }
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
               require(declaredInput.typeParameters().size == providedInput.typeParameters().size) { "Expected that the number of type parameters for input $index would be the same in the function declaration and the provided inputs.  Expected ${declaredInput.typeParameters().size} but found ${providedInput.typeParameters().size}" }
               resolveTypeArgumentFromInputTypes(
                  typeArgument,
                  declaredInput.typeParameters(),
                  providedInput.typeParameters()
               )
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
         val type = replaceType(parameter.type,resolvedArguments)
         if (type != parameter.type) {
            parameter.copy(type = type)
         } else {
            parameter
         }
      }
   }
   fun replaceType(type: Type, resolvedArguments: Map<TypeArgument, Type>):Type {
      return when {
         type is TypeArgument -> resolvedArguments[type] ?: error("No resolution possible for type ${type.declaredName}")
         type is GenericType -> {
            val replacedTypeParameters = type.typeParameters().map { replaceType(it,resolvedArguments) }
            type.withParameters(replacedTypeParameters).getOrHandle { throw RuntimeException(it.message) }
         }
         type is LambdaExpressionType -> {
            val replacedReturnType = replaceType(type.returnType, resolvedArguments)
            val replacedParameterTypes = type.parameterTypes.map { replaceType(it,resolvedArguments) }
            val replaced = type.copy(parameterTypes = replacedParameterTypes, returnType = replacedReturnType)
            replaced
         }

         else -> type
      }
   }
}
