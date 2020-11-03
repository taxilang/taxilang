package lang.taxi.compiler

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.types.GenericType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.TypeAlias
import org.antlr.v4.runtime.ParserRuleContext

fun Type.isAssignableTo(assignmentTargetType: Type, considerTypeParameters: Boolean = true): Boolean {
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

fun Type.inheritsFrom(other:Type, considerTypeParameters: Boolean = true):Boolean {
   if (this.resolvesSameAs(other)) {
      return true
   }
   val unaliasedOther = other.resolveAliases()
   return (this.allInheritedTypes + this).any { inheritedType ->
      val unaliasedInheritedType = inheritedType.resolveAliases()
      unaliasedInheritedType.resolvesSameAs(unaliasedOther, considerTypeParameters)
   }
}
object TypeChecking {

   fun isAssignableTo(valueType: Type, assignmentTargetType: Type, considerTypeParameters: Boolean = true): Boolean {
      val valueTypeWithoutAliases = valueType.resolveAliases()
      val assignmentTargetTypeWithoutAliases = assignmentTargetType.resolveAliases()

      if (valueTypeWithoutAliases.resolvesSameAs(assignmentTargetTypeWithoutAliases, considerTypeParameters)) {
         return true
      }

      // We allow naked primitives to be assigned to compatible
      // subtypes.  This allows assignments like xpath() and jsonPath() to work
      if (valueTypeWithoutAliases is PrimitiveType && assignmentTargetTypeWithoutAliases.basePrimitive == valueTypeWithoutAliases) {
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




   fun assertIsAssignable(valueType: Type, receiverType: Type, token: ParserRuleContext): CompilationError? {
      // This is a first pass, pretty sure this is naieve.
      // Need to take the Vyne implmentation at Type.kt
      return when {
         // ValueType being an Any could happen in the else branch of a when clause, if using
         // an accessor (such as column/jsonPath/xpath) , where we can't infer the value type returned.
         valueType.basePrimitive == PrimitiveType.ANY -> null
         receiverType.basePrimitive == PrimitiveType.ANY -> null
         valueType.isAssignableTo(receiverType) -> null
//         receiverType.basePrimitive == valueType.basePrimitive -> null
         else -> CompilationError(token.start, "Type mismatch.  Type of ${valueType.qualifiedName} is not assignable to type ${receiverType.qualifiedName}")
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
