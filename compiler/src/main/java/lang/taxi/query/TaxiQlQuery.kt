package lang.taxi.query

import lang.taxi.types.*


data class TaxiQlQuery(
   val name: String,
   val facts: List<Variable>,
   val queryMode: QueryMode,
   val parameters: Map<String, QualifiedName>,
   val typesToFind: List<DiscoveryType>,
   val projectedType: ProjectedType?
)
