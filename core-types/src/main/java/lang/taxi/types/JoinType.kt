package lang.taxi.types

import arrow.core.Either

/**
 * A joinType represents a type described by a joinTo
 * clause.
 *
 * It contains a single sourceType, and multiple
 * targetTypes.
 *
 * It is not expected that joinTypes have a purpose
 * outside of describing query services.
 *
 * If considerings "should I use this, or a Union type",
 * the answer is probably a Union type.
 */
class JoinType(
   override val qualifiedName: String,
   override var definition: JoinTypeDefinition?
) : UserType<JoinTypeDefinition, NotExtendable> {
   companion object {
      fun joinTypeName(source: Type, targets: List<Type>): String {
         return "JoinTypeFrom${source.qualifiedName}To${targets.joinToString(separator = "_") { it.qualifiedName }}"
      }
   }

   override val extensions: List<NotExtendable> = emptyList()
   override val referencedTypes: List<Type>
      get() {
         return if (isDefined) {
            (definition!!.rightTypes + definition!!.leftType)
               .filterIsInstance<UserType<*, *>>()
               .flatMap { it.referencedTypes }
         } else {
            emptyList()
         }
      }

   override fun addExtension(extension: NotExtendable): Either<ErrorMessage, NotExtendable> {
      error("Extensions to JoinTypes are not supported")
   }

   val leftType: Type
      get() {
         return definition!!.leftType
      }

   val rightTypes: List<Type>
      get() {
         return definition!!.rightTypes
      }

   override val inheritsFrom: Set<Type> = emptySet()
   override val allInheritedTypes: Set<Type> = emptySet()
   override val format: List<String>? = null
   override val inheritsFromPrimitive: Boolean = false
   override val basePrimitive: PrimitiveType? = null
   override val formattedInstanceOfType: Type? = null
   override val definitionHash: String?
      get() = TODO("Not yet implemented")
   override val offset: Int? = null
   override val typeKind: TypeKind? = TypeKind.Model
   override val typeDoc: String?
      get() = if (isDefined) {
         definition?.typeDoc
      } else {
         null
      }
}

data class JoinTypeDefinition(
   val leftType: Type,
   val rightTypes: List<Type>,
   override val typeDoc: String?,
   override val compilationUnit: CompilationUnit
) : TypeDefinition, Documented
