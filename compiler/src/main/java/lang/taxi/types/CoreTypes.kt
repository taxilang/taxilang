package lang.taxi.types

import lang.taxi.SourceCode
import lang.taxi.Type
import lang.taxi.TypeSystem

interface GenericType : Type {
   val parameters: List<Type>

   fun resolveTypes(typeSystem: TypeSystem): GenericType
}

data class ArrayType(val type: Type, val source:SourceCode = SourceCode.unspecified()) : GenericType {
   override val sources: List<SourceCode> = listOf(source)
   override fun resolveTypes(typeSystem: TypeSystem): GenericType {
      return this.copy(type = typeSystem.getType(type.qualifiedName))
   }

   override val qualifiedName: String = "lang.taxi.Array"
   override val parameters: List<Type> = listOf(type)
}
