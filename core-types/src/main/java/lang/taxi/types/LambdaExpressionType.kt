package lang.taxi.types

import kotlin.Annotation

/**
 * A type in parameters which is a function.
 * eg:
 *  declare function <T,A> reduce(T[], (T,A) -> A):A
 *
 *  It's the (T,A) -> A part of the above
 */
data class LambdaExpressionType(
   override val qualifiedName: String,
   val parameterTypes:List<Type>,
   val returnType:Type,
   override val compilationUnits: List<CompilationUnit>,

):Type {
   private val wrapper = LazyLoadingWrapper(this)
   override val inheritsFrom: Set<Type> = emptySet()
   override val allInheritedTypes: Set<Type> = wrapper.allInheritedTypes
   override val format: List<String>? = null
   override val inheritsFromPrimitive: Boolean = wrapper.inheritsFromPrimitive
   override val basePrimitive: PrimitiveType? = wrapper.basePrimitive
   override val formattedInstanceOfType: Type? = null
   override val definitionHash: String? = wrapper.definitionHash
//   override val calculation: Formula? = null
   override val offset: Int? = null
   override val typeKind: TypeKind? = TypeKind.Type
   override val typeDoc: String? = null

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()

}
