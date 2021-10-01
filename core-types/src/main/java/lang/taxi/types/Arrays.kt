package lang.taxi.types

object Arrays {
   /**
    * Returns a name of a type where the collection type is the provided member
    * eg:  for T returns the name of T[]
    */
   fun nameOfArray(memberType:QualifiedName):QualifiedName {
      return QualifiedName.from(ArrayType.NAME).copy(parameters = listOf(memberType))
   }

   fun isArray(qualifiedName: QualifiedName): Boolean {
      return qualifiedName.toString() == ArrayType.NAME
   }
   fun isArray(type:Type) : Boolean {
     return Arrays.isArray(type.toQualifiedName())
   }

   fun arrayOf(type: Type): Type {
      return ArrayType(type, CompilationUnit.unspecified())
   }
}
