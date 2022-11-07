package lang.taxi.query

import lang.taxi.types.*


data class TaxiQlQuery(
   val name: String,
   val facts: List<Variable>,
   val queryMode: QueryMode,
   val parameters: Map<String, QualifiedName>,
   val typesToFind: List<DiscoveryType>,
   val projectedType: Type?
) {
   val projectedObjectType : ObjectType
      get() {
         return when (projectedType) {
            null -> error("ProjectType is null")
            is ArrayType -> projectedType.type as ObjectType
            is ObjectType -> projectedType
            else -> error("Cannot cast ${projectedType::class.simpleName} to ObjectType")
         }
      }


}
