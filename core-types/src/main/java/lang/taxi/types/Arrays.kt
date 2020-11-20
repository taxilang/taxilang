package lang.taxi.types

object Arrays {
   fun nameOfArray(memberType:QualifiedName):QualifiedName {
      return QualifiedName.from(ArrayType.NAME).copy(parameters = listOf(memberType))
   }

   fun isArray(qualifiedName: QualifiedName): Boolean {
      return qualifiedName.toString() == ArrayType.NAME
   }
}
