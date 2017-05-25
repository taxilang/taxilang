package lang.taxi

import lang.taxi.types.ObjectType

interface Type {
   val qualifiedName: String
}

data class TaxiDocument(val namespace: String?,
                        val types: List<Type>
) {
   private val typeMap = types.associateBy { it.qualifiedName }
   fun type(name: String): Type {
      return typeMap[name]!!
   }
   fun objectType(name:String): ObjectType {
      return type(name) as ObjectType
   }
}
