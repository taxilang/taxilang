package lang.taxi.types

import lang.taxi.toggles.FeatureToggle


class TypeChecker(val enabled:FeatureToggle = FeatureToggle.DISABLED) {
   companion object {
      val DEFAULT = TypeChecker()
   }

   fun isAssignableTo(valueType: Type, assignmentTargetType: Type, considerTypeParameters: Boolean = true): Boolean {
      val valueTypeWithoutAliases = valueType.resolveAliases()
      val assignmentTargetTypeWithoutAliases = assignmentTargetType.resolveAliases()

      if (valueTypeWithoutAliases.resolvesSameAs(assignmentTargetTypeWithoutAliases, considerTypeParameters)) {
         return true
      }

      if (assignmentTargetType is EnumType) {
         return valueType.inheritsFrom(assignmentTargetType) ||
            // Allow naked Strings to be assigned to enums.
            // This allows name matching
            valueTypeWithoutAliases == PrimitiveType.STRING
      }

      // We allow naked primitives to be assigned to compatible
      // subtypes.  This allows assignments like xpath() and jsonPath() to work
      if (valueTypeWithoutAliases is PrimitiveType &&
         assignmentTargetTypeWithoutAliases.basePrimitive == valueTypeWithoutAliases) {
         return true
      }

      // TypeArguments are expressions for types that aren't yet
      // resolved. In future we could consider a smarter way to resolve these, but for now,
      // can't work out how, so assume assignable.
      if (containsTypeArgument(assignmentTargetType) || containsTypeArgument(valueType)) {
         return true
      }

      // Here, assignmentTargetType is something like (T) -> Boolean
      // and valueType is something that should return Boolean.
      // So, check assignment on the return type of assignmentTargetType (ie., the boolean in (T) -> Boolean)
      if (assignmentTargetTypeWithoutAliases is LambdaExpressionType) {
         return isAssignableTo(valueType, assignmentTargetTypeWithoutAliases.returnType, considerTypeParameters)
      }


      // Bail out early
      if (considerTypeParameters && valueTypeWithoutAliases.typeParameters().size != assignmentTargetTypeWithoutAliases.typeParameters().size) {
         return false
      }



      // Variance rules (simple implementation)
      if (considerTypeParameters && valueTypeWithoutAliases.typeParameters().isNotEmpty()) {
         // To check variance rules, we check that each of the raw types are assignable.
         // This feels like a naieve implementation.
         if (!isAssignableTo(assignmentTargetTypeWithoutAliases, valueTypeWithoutAliases, considerTypeParameters = false)) {
            return false
         }
         valueTypeWithoutAliases.typeParameters().forEachIndexed { index, type ->
            val otherParamType = assignmentTargetTypeWithoutAliases.typeParameters()[index].resolveAliases()
            val thisParamType = type.resolveAliases()
            if (!thisParamType.isAssignableTo(otherParamType)) {
               return false
            }
         }
         return true
      } else {
         return valueTypeWithoutAliases.inheritsFrom(assignmentTargetTypeWithoutAliases, considerTypeParameters)
      }


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

   fun resolvesSameAs(typeA:Type, typeB: Type, considerTypeParameters: Boolean = true): Boolean {
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
}
