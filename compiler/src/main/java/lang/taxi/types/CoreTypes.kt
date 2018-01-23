package lang.taxi.types

import lang.taxi.CompilationUnit
import lang.taxi.Type
import lang.taxi.TypeSystem

interface GenericType : Type {
   val parameters: List<Type>

   fun resolveTypes(typeSystem: TypeSystem): GenericType
}

data class ArrayType(val type: Type, val source: CompilationUnit) : GenericType {
   override fun resolveTypes(typeSystem: TypeSystem): GenericType {
      return this.copy(type = typeSystem.getType(type.qualifiedName))
   }


   override val compilationUnits: List<CompilationUnit> = listOf(source)
   override val qualifiedName: String = "lang.taxi.Array"
   override val parameters: List<Type> = listOf(type)
}
