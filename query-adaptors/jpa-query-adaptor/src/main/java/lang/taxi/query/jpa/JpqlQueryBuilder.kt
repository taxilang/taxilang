package lang.taxi.query.jpa

import lang.taxi.TaxiDocument
import lang.taxi.query.TaxiQlQuery
import lang.taxi.services.operations.constraints.ConstantValueExpression
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.PropertyTypeIdentifier
import lang.taxi.types.DiscoveryType
import lang.taxi.utils.quotedIfNecessary

class JpqlQueryBuilder {
   fun convert(schema: TaxiDocument, query: TaxiQlQuery, types: List<JpaQueryType>): String {
      val typeShortNames = types.toShortNames()
      val criterion = convertConstraintsToCriteria(query, typeShortNames, schema, types)
      val whereClause = if (criterion.isEmpty())  ""  else  criterion.joinToString(" AND ", prefix = "WHERE ")
      return """select ${typeShortNames.values.joinToString(", ")} from
         |${typeShortNames.map { (jpaName, shortName) -> "$jpaName $shortName" }.joinToString(", ")}
         |$whereClause
      """.trimMargin()
         .trim()
      // TODO : criteria
   }

   private fun convertConstraintsToCriteria(
      query: TaxiQlQuery,
      typeShortNames: Map<JpaTypeName, JpaShortTypeName>,
      schema: TaxiDocument,
      jpaTypes: List<JpaQueryType>
   ): List<String> {
      return query.typesToFind
         .flatMap { discoveryType ->
            discoveryType.constraints.map { constraint ->
               val jpaType = jpaTypes.first { it.discoveryType == discoveryType }
               val shortName = typeShortNames[jpaType.jpaType.simpleName]
                  ?: error("Expected to find a type shortname for type ${jpaType.jpaType.simpleName}")
               convertConstraintToCriteria(constraint, discoveryType, schema, jpaType, shortName)
            }
         }
   }

   private fun convertConstraintToCriteria(
      constraint: Constraint,
      discoveryType: DiscoveryType,
      schema: TaxiDocument,
      jpaType: JpaQueryType,
      shortName: JpaShortTypeName
   ): String {
      return when (constraint) {
         is PropertyToParameterConstraint -> convertConstraintToCriteria(
            constraint,
            discoveryType,
            schema,
            jpaType,
            shortName
         )
         else -> TODO("Jpql generation not built for constraint type ${constraint::class.simpleName}")
      }
   }

   private fun convertConstraintToCriteria(
      constraint: PropertyToParameterConstraint,
      discoveryType: DiscoveryType,
      schema: TaxiDocument,
      jpaType: JpaQueryType,
      shortName: JpaShortTypeName
   ): String {
      val propertyIdentifier = constraint.propertyIdentifier
      val jpaTypeField = when(propertyIdentifier) {
         is PropertyTypeIdentifier ->  jpaType.findFieldWithType(propertyIdentifier.type)
         else -> error("jqpl criteria generation not built for PropertyIdentifier of type ${propertyIdentifier::class.simpleName}")
      }
      val expectedValueExpression = constraint.expectedValue.let { valueExpression ->
         when(valueExpression) {
            is ConstantValueExpression -> valueExpression.value.quotedIfNecessary(quoteChar = "'", quoteToReplace = "\"")
            else -> TODO("jpql value expression generation not build for value expression of type ${valueExpression::class.simpleName}")
         }
      }
      val fieldName = jpaTypeField.name
      return "${shortName}.$fieldName ${constraint.operator.symbol} $expectedValueExpression"
   }
}

typealias JpaTypeName = String
typealias JpaShortTypeName = String

private fun List<JpaQueryType>.toShortNames(): Map<JpaTypeName, JpaShortTypeName> {
   return this.mapIndexed { index, jpaQueryType -> jpaQueryType.jpaType.simpleName to "t$index" }
      .toMap()
}

private fun List<JpaQueryType>.asJpqlTypeList(): String {
   return this.joinToString(", ") { it.jpaType.simpleName }
}
