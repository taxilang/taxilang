package lang.taxi.types

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.toggles.FeatureToggle
import lang.taxi.types.StructuralTypeChecker.structuralCompatibilityApplies


class TypeChecker(val enabled: FeatureToggle = FeatureToggle.DISABLED) {
   companion object {
      val DEFAULT = TypeChecker()
      val TypesAreAssignable:Either<String,Boolean> = true.right()
   }

   fun isAssignableTo(valueType: Type, assignmentTargetType: Type, considerTypeParameters: Boolean = true, permitStructurallyCompatible: Boolean = true): Boolean {
     return isAssignableOrErrors(valueType,assignmentTargetType, considerTypeParameters, permitStructurallyCompatible)
        .isRight()
   }

   /**
    * Type arguments are placeholders in expressions for types.
    * eg:
    *  declare function <T,A> reduce(T[], (T,A) -> A):A
    *
    * The T & A in the above are type arguments. (Not the same as T in Stream<T>)
    */
   private fun containsTypeArgument(type: Type): Boolean {
      return when (type) {
         is TypeArgument -> true
         is TypeReference -> containsTypeArgument(type.type)
         is LambdaExpressionType -> containsTypeArgument(type.returnType)
         else -> false
      }
   }

   fun resolvesSameAs(typeA: Type, typeB: Type, considerTypeParameters: Boolean = true): Boolean {
      val unaliasedTypeA = TypeAlias.underlyingType(typeA.resolveAliases())
      val unaliasedTypeB = TypeAlias.underlyingType(typeB.resolveAliases())


      if (considerTypeParameters && (unaliasedTypeA.typeParameters().size != unaliasedTypeB.typeParameters().size)) {
         return false
      }

      val matchesOnName = (unaliasedTypeA.qualifiedName == unaliasedTypeB.qualifiedName)

      val parametersMatch = if (considerTypeParameters) {
         unaliasedTypeA.typeParameters().all { parameterType ->
            val index = unaliasedTypeA.typeParameters().indexOf(parameterType)
            val otherParameterType = unaliasedTypeB.typeParameters()[index]
            parameterType.resolvesSameAs(otherParameterType)
         }
      } else {
         true
      }
      return matchesOnName && parametersMatch
   }

   fun isAssignableOrErrors(
      valueType: Type, assignmentTargetType: Type, considerTypeParameters: Boolean = true,
      permitStructurallyCompatible: Boolean = true
   ): Either<String, Boolean> {
      fun failWithReason(reason: String): Either<String,Boolean> {
         return "Type ${valueType.toQualifiedName().shortDisplayName} is not assignable to ${assignmentTargetType.toQualifiedName().shortDisplayName}: reason".left()
      }
      val valueTypeWithoutAliases = valueType.resolveAliases()
      val assignmentTargetTypeWithoutAliases = assignmentTargetType.resolveAliases()

      if (valueTypeWithoutAliases == PrimitiveType.NOTHING) {
         return TypesAreAssignable
      }
      if (assignmentTargetTypeWithoutAliases == PrimitiveType.ANY) {
         return TypesAreAssignable
      }
      if (valueTypeWithoutAliases.resolvesSameAs(assignmentTargetTypeWithoutAliases, considerTypeParameters)) {
         return TypesAreAssignable
      }

      // TypeArguments are expressions for types that aren't yet
      // resolved. In future we could consider a smarter way to resolve these, but for now,
      // can't work out how, so assume assignable.
      if (containsTypeArgument(assignmentTargetType) || containsTypeArgument(valueType)) {
         return TypesAreAssignable
      }

      if (assignmentTargetType is EnumType) {
         return if (valueType.inheritsFrom(assignmentTargetType) ||
            // Allow naked Strings to be assigned to enums.
            // This allows name matching
            valueTypeWithoutAliases == PrimitiveType.STRING) {
            TypesAreAssignable
         } else {
            failWithReason("Enum type assignability mismatch")
         }
      }

      // We allow naked primitives to be assigned to compatible
      // subtypes.  This allows assignments like xpath() and jsonPath() to work
      if (valueTypeWithoutAliases is PrimitiveType &&
         assignmentTargetTypeWithoutAliases.basePrimitive == valueTypeWithoutAliases
      ) {
         return TypesAreAssignable
      }



      // Here, assignmentTargetType is something like (T) -> Boolean
      // and valueType is something that should return Boolean.
      // So, check assignment on the return type of assignmentTargetType (ie., the boolean in (T) -> Boolean)
      if (assignmentTargetTypeWithoutAliases is LambdaExpressionType) {
         return isAssignableOrErrors(valueType, assignmentTargetTypeWithoutAliases.returnType, considerTypeParameters)
      }


      // Bail out early
      if (considerTypeParameters && valueTypeWithoutAliases.typeParameters().size != assignmentTargetTypeWithoutAliases.typeParameters().size) {
         return failWithReason("Mismatch in type parameters")
      }


      // Variance rules (simple implementation)
      if (considerTypeParameters && valueTypeWithoutAliases.typeParameters().isNotEmpty()) {
         // To check variance rules, we check that each of the raw types are assignable.
         // This feels like a naieve implementation.
         if (!isAssignableTo(
               assignmentTargetTypeWithoutAliases,
               valueTypeWithoutAliases,
               considerTypeParameters = false
            )
         ) {
            return failWithReason("Types with parameters have incompatible raw types")
         }
         valueTypeWithoutAliases.typeParameters().forEachIndexed { index, type ->
            val otherParamType = assignmentTargetTypeWithoutAliases.typeParameters()[index].resolveAliases()
            val thisParamType = type.resolveAliases()
            if (!thisParamType.isAssignableTo(otherParamType)) {
               return failWithReason("Parameter type ${thisParamType.qualifiedName} is not compatible with parameter type ${otherParamType.qualifiedName}")
            }
         }
         return TypesAreAssignable
      }

      // Check if valueType is structurally compatible with assignmentTargetType
      if (permitStructurallyCompatible && structuralCompatibilityApplies(valueTypeWithoutAliases, assignmentTargetTypeWithoutAliases)) {
         // Before applying structural checks, inheritance rules take precedence:
         if (valueTypeWithoutAliases.inheritsFrom(assignmentTargetTypeWithoutAliases, considerTypeParameters)) {
            return TypesAreAssignable
         }
         // If structural compatability rules apply, and they do not share a type hierarchy, then structural
         // rules must pass
         return StructuralTypeChecker.structurallyCompatibleOrErrors(valueTypeWithoutAliases, assignmentTargetTypeWithoutAliases)
      }

      return if (valueTypeWithoutAliases.inheritsFrom(assignmentTargetTypeWithoutAliases, considerTypeParameters)) {
         TypesAreAssignable
      } else {
         failWithReason("Types have incompatible hierarchies")
      }
   }
}
