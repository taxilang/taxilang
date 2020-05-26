package lang.taxi.types

object Arrays {
   fun nameOfArray(memberType:QualifiedName):QualifiedName {
      return QualifiedName.from(PrimitiveType.ARRAY.qualifiedName).copy(parameters = listOf(memberType))
   }

   fun isArray(qualifiedName: QualifiedName): Boolean {
      return qualifiedName.toString() == PrimitiveType.ARRAY.qualifiedName
   }
}
