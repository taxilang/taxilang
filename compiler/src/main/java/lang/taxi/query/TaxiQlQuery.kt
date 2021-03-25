package lang.taxi.query

import lang.taxi.types.DiscoveryType
import lang.taxi.types.ProjectedType
import lang.taxi.types.QualifiedName
import lang.taxi.types.QueryMode
import lang.taxi.types.Type
import lang.taxi.types.TypedValue


data class TaxiQlQuery(
   val name: String,
   val facts: Map<String, TypedValue>,
   val queryMode: QueryMode,
   val parameters: Map<String, QualifiedName>,
   val typesToFind: List<DiscoveryType>,
   val projectedType: ProjectedType?
)
