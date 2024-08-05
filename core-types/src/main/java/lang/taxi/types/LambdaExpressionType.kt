package lang.taxi.types

import lang.taxi.types.PrimitiveType.Companion.INHERITS_FROM_ANY

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
   override val inheritsFrom: List<Type> = INHERITS_FROM_ANY
   override val allInheritedTypes: Set<Type> = wrapper.allInheritedTypes
   override val format: List<String>? = null
   override val inheritsFromPrimitive: Boolean = wrapper.inheritsFromPrimitive
   override val basePrimitive: PrimitiveType? = wrapper.basePrimitive
   override val definitionHash: String? = wrapper.definitionHash
   override val formatAndZoneOffset: FormatsAndZoneOffset?= null
   override val offset: Int? = null
   override val typeKind: TypeKind? = TypeKind.Type
   override val typeDoc: String? = null
   override val isScalar: Boolean = false

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()

}
