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
         return valueType.inheritsFrom(assignmentTargetType)
      }

      // We allow naked primitives to be assigned to compatible
      // subtypes.  This allows assignments like xpath() and jsonPath() to work
      if (valueTypeWithoutAliases is PrimitiveType &&
         assignmentTargetTypeWithoutAliases.basePrimitive == valueTypeWithoutAliases) {
         return true
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
