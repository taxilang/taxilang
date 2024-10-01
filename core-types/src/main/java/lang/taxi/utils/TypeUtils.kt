package lang.taxi.utils

import lang.taxi.types.EnumType
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type
import lang.taxi.types.TypeArgument

object TypeUtils {
   fun getMostSpecificType(typeA: Type, typeB: Type): Type {
      if (!typeB.isAssignableTo(typeA) && !typeA.isAssignableTo(typeB)) {
         error("Cannot pick most specific type between ${typeA.toQualifiedName().parameterizedName} and ${typeB.toQualifiedName().parameterizedName} as they are incompatible")
      }
      return when {
         typeA == PrimitiveType.ANY && typeB != PrimitiveType.ANY -> typeB
         typeB == PrimitiveType.ANY && typeA != PrimitiveType.ANY -> typeA
         typeA.inheritsFrom(typeB) -> typeA
         typeB.inheritsFrom(typeA) -> typeB
         typeA is TypeArgument -> typeB
         typeB is TypeArgument -> typeA
         typeA is EnumType && typeB !is EnumType -> typeA
         typeB is EnumType && typeA !is EnumType -> typeB
         else -> error("Cannot determine most specific type between  ${typeA.toQualifiedName().parameterizedName} and ${typeB.toQualifiedName().parameterizedName} ")
      }
   }
}
