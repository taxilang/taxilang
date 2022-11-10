package lang.taxi.types

/**
 * Represents an import which has not been resolved, and no definition
 * has yet been discovered.
 *
 * This is typically produced as a marker class for code-gen tooling, rather than
 * from within the compiler itself.
 *
 * (eg., from with the Java annotation frameworks that wish to make reference
 * to an import that needs to be generated, but without having to fetch the
 * type directly, which is not required at codegen time)
 *
 */
class UnresolvedImportedType(override val qualifiedName: String) : Type {
   override val formatAndZoneOffset: FormatsAndZoneOffset?= null
   override val compilationUnits: List<CompilationUnit>
      get() = TODO("Not yet implemented")
   override val inheritsFrom: kotlin.collections.Set<lang.taxi.types.Type>
      get() = TODO("Not yet implemented")
   override val allInheritedTypes: kotlin.collections.Set<lang.taxi.types.Type>
      get() = TODO("Not yet implemented")
   override val format: List<String>?
      get() = TODO("Not yet implemented")
   override val inheritsFromPrimitive: kotlin.Boolean
      get() = TODO("Not yet implemented")
   override val basePrimitive: lang.taxi.types.PrimitiveType?
      get() = TODO("Not yet implemented")
   override val definitionHash: kotlin.String?
      get() = TODO("Not yet implemented")
//   override val calculation: lang.taxi.types.Formula?
//      get() = TODO("Not yet implemented")
   override val offset: Int?
      get() = TODO("Not yet implemented")
   override val typeDoc: String?
      get() = TODO("Not yet implemented")
   override val typeKind: TypeKind?
      get() = TODO("Not yet implemented")

   // Not currently implemented, but could be in the future
   override val annotations: List<lang.taxi.types.Annotation> = emptyList()


}
