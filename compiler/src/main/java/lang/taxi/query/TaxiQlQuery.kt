package lang.taxi.query

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.types.ArrayType
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type


data class TaxiQlQuery(
   val name: String,
   val facts: List<Parameter>,
   val queryMode: QueryMode,
   val parameters: List<Parameter>,
   val typesToFind: List<DiscoveryType>,
   val projectedType: Type?,
   val projectionScope:  ProjectionFunctionScope?
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
