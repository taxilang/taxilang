package lang.taxi.types

import arrow.core.Either

data class UnionType(
   override val qualifiedName: String,
   override var definition: UnionTypeDefinition?,
) : UserType<UnionTypeDefinition, UnionTypeExtension>, Annotatable {


   val types: List<Type> = definition!!.types

   companion object {
      fun unionTypeName(types: List<Type>) = types.joinToString(separator = "|") { it.qualifiedName }
      fun combine(left:Type, right:Type, compilationUnit: CompilationUnit):UnionType {
         val types = listOf(left,right).flatMap { type ->
            if (type is UnionType) {
               type.types
            } else {
               listOf(type)
            }
         }
         return UnionType(
            unionTypeName(types),
            UnionTypeDefinition(
               types,
               compilationUnit
            )
         )

      }
   }


   override val extensions: List<UnionTypeExtension> = emptyList()
   override val referencedTypes: List<Type>
      get() = this.types
         .filterIsInstance<ObjectType>()
         .flatMap { it.referencedTypes }

   override fun addExtension(extension: UnionTypeExtension): Either<ErrorMessage, UnionTypeExtension> {
      error("Extensions are not supported on union types")
   }

   override val inheritsFrom: Set<Type> = emptySet()
   override val allInheritedTypes: Set<Type> = emptySet()
   override val format: List<String> = emptyList()
   override val inheritsFromPrimitive: Boolean = false
   override val basePrimitive: PrimitiveType? = null
   override val formattedInstanceOfType: Type? = null
   override val definitionHash: String? = null
   override val offset: Int? = null
   override val typeKind: TypeKind = TypeKind.Type

   override val typeDoc: String? = definition!!.typeDoc
   override val annotations: List<Annotation> = definition!!.annotations
}

data class UnionTypeDefinition(
   val types: List<Type>,
   override val compilationUnit: CompilationUnit,
   override val annotations: List<Annotation> = emptyList(),
   override val typeDoc: String? = null,

   ) : TypeDefinition, Annotatable, Documented

// I don't think we allow actual extensions of union types,
// but the compiler requires it .
data class UnionTypeExtension(
   override val compilationUnit: CompilationUnit
) : TypeDefinition


object NotExtendable : TypeDefinition {
   override val compilationUnit: CompilationUnit
      get() = TODO("Not yet implemented")

}
