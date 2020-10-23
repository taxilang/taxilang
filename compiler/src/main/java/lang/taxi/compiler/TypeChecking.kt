package lang.taxi.compiler

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.types.GenericType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import org.antlr.v4.runtime.ParserRuleContext

fun Type.isAssignableTo(assignmentTargetType: Type, considerTypeParameters: Boolean): Boolean {
   return TypeChecking.isAssignableTo(this, assignmentTargetType, considerTypeParameters)
}

fun Type.resolveAliases(): Type {
   return TypeAlias.underlyingType(this)
}

fun Type.resolvesSameAs(other: Type, considerTypeParameters: Boolean = true):Boolean {
   return TypeChecking.resolvesSameAs(this,other,considerTypeParameters)
}

fun Type.typeParameters():List<Type> {
   return when (this) {
      is GenericType -> this.parameters
      else -> emptyList()
   }
}

object TypeChecking {

   fun isAssignableTo(valueType: Type, assignmentTargetType: Type, considerTypeParameters: Boolean = true): Boolean {
      val valueTypeWithoutAliases = valueType.resolveAliases()
      val assignmentTargetTypeWithoutAliases = assignmentTargetType.resolveAliases()

      if (valueTypeWithoutAliases.resolvesSameAs(assignmentTargetTypeWithoutAliases, considerTypeParameters)) {
         return true
      }

      // Bail out early
      if (considerTypeParameters && thisWithoutAliases.typeParameters.size != otherWithoutAliases.typeParameters.size) {
         return false
      }

      // Variance rules (simple implementation)
      if (considerTypeParameters && thisWithoutAliases.typeParameters.isNotEmpty()) {
         // To check variance rules, we check that each of the raw types are assignable.
         // This feels like a naieve implementation.
         if (!isAssignableTo(otherWithoutAliases, considerTypeParameters = false)) {
            return false
         }
         thisWithoutAliases.typeParameters.forEachIndexed { index, type ->
            val otherParamType = otherWithoutAliases.typeParameters[index].resolveAliases()
            val thisParamType = type.resolveAliases()
            if (!thisParamType.isAssignableTo(otherParamType)) {
               return false
            }
         }
         return true
      } else {
         return thisWithoutAliases.inheritsFrom(otherWithoutAliases, considerTypeParameters)
      }


   }

   /**
    * Walks down the entire chain of aliases until it hits the underlying non-aliased
    * type
    */
   fun resolveAliases(): Type {
      val resolvedFormattedType = resolveUnderlyingFormattedType()
      return if (!resolvedFormattedType.isTypeAlias) {
         resolvedFormattedType
      } else {
         // Experiment...
         // type aliases for primtiives are a core building block for taxonomies
         // But they're causing problems :
         // type alias Height as Int
         // type alias Weight as Int
         // We clearly didn't mean that Height = Weight
         // Ideally, we need better constructrs in the langauge to suport definint the primitve types.
         // For now, let's stop resolving aliases one step before the primitive
         when {
            aliasForTypeName!!.fullyQualifiedName == PrimitiveType.ARRAY.qualifiedName -> resolvedFormattedType.aliasForType!!.resolveAliases()
            resolvedFormattedType.aliasForType!!.isPrimitive -> this
            else -> resolvedFormattedType.aliasForType!!.resolveAliases()
         }
      }
   }

   fun resolvesSameAs(typeA:Type, typeB: Type, considerTypeParameters: Boolean = true): Boolean {
      val unaliasedTypeA = typeA.resolveAliases()
      val unaliasedTypeB = typeB.resolveAliases()


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

   /**
    * Returns true if this type is either the same as, or inherits
    * from the other type.
    *
    * Including checking for equivalent types in the inheritsFrom
    * matches the JVM convention.
    */
   fun inheritsFrom(other: Type, considerTypeParameters: Boolean = true): Boolean {
      if (this.resolveAliases().resolvesSameAs(other.resolveAliases())) {
         return true
      }
      val otherType = other.resolveAliases()
      val result = (this.inheritanceGraph + this).any { thisType ->
         val thisUnaliased = thisType.resolveAliases()
         thisUnaliased.resolvesSameAs(otherType, considerTypeParameters)
      }
      return result
   }


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
