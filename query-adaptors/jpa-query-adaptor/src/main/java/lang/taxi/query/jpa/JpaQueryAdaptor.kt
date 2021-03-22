package lang.taxi.query.jpa

import com.google.common.annotations.VisibleForTesting
import lang.taxi.CompilationError
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.TypeNames
import lang.taxi.annotations.DataType
import lang.taxi.generators.java.TaxiGenerator
import lang.taxi.messages.Severity
import lang.taxi.packages.utils.log
import lang.taxi.query.TaxiQlQuery
import lang.taxi.types.Arrays
import lang.taxi.types.DiscoveryType
import lang.taxi.types.QualifiedName
import java.lang.reflect.Field
import javax.persistence.EntityManager
import javax.persistence.metamodel.ManagedType

class JpaQueryAdaptor(private val entityManager: EntityManager) {
   private val jpqlQueryBuilder = JpqlQueryBuilder()
   private val taxiDoc: TaxiDocument
   private val compilationMessages: List<CompilationError>
   val managedTaxiTypes: Map<QualifiedName, ManagedType<*>>

   val compilationErrors: List<CompilationError>
      get() {
         return compilationMessages.filter { it.severity == Severity.ERROR }
      }

   val hasErrors: Boolean
      get() {
         return compilationErrors.isNotEmpty()
      }

   init {
      this.managedTaxiTypes = entityManager.metamodel.managedTypes
         .mapNotNull { managedType ->
            managedType.javaType.getDeclaredAnnotation(DataType::class.java)?.let {
               QualifiedName.from(TypeNames.deriveTypeName(managedType.javaType)) to managedType
            }
         }.toMap()
      val javaTypes = this.managedTaxiTypes.values.map { managedType -> managedType.javaType }
      val schema = TaxiGenerator()
         .forClasses(javaTypes)
         .generateAsStrings()
      val (compilationMessages, taxi) = Compiler.forStrings(schema).compileWithMessages()
      this.compilationMessages = compilationMessages
      this.taxiDoc = taxi
   }

   fun execute(taxiQl: String): List<Any?> {
      val queries = Compiler(taxiQl, importSources = listOf(taxiDoc))
         .queries()
      require(queries.size == 1) { "Expected a single query, but found ${queries.size}" }
      val query = queries.first()
      val jpaQueryTypes = getJpaQueryTypes(query)
      val jpql = jpqlQueryBuilder.convert(taxiDoc, query, jpaQueryTypes)
      log().info("Query translated.  Taxiql: $taxiQl \n jpql: $jpql")
      return entityManager.createQuery(jpql).resultList
   }

   @VisibleForTesting
   internal fun getJpaQueryTypes(query: TaxiQlQuery): List<JpaQueryType> {
      return query.typesToFind.map { discoveryType ->
         val typeName = JpaQueryType.queryTypeName(discoveryType)
         val managedType =
            managedTaxiTypes.getOrElse(typeName) { error("Type $typeName is not mapped to a JPA entity") }
         JpaQueryType(discoveryType, managedType.javaType)

      }
   }


}

data class JpaQueryType(val discoveryType: DiscoveryType, val jpaType: Class<*>) {
   fun findFieldWithType(type: QualifiedName): Field {
      return jpaType.declaredFields
         .firstOrNull { field ->
            field.getAnnotation(DataType::class.java)?.let { dataType ->
               val fieldTypeName = TypeNames.deriveTypeName(field, "")
               fieldTypeName == type.toString()
            } ?: false
         } ?: error("No field exists on java type ${jpaType.simpleName} with DataType annotation matching type $type")

   }

   companion object {
      fun from(query: TaxiQlQuery, classes: List<Class<*>>): List<JpaQueryType> {
         val classMap = classes.associateBy { QualifiedName.from(TypeNames.deriveTypeName(it)) }
         return query.typesToFind.map { discoveryType ->
            val queryTypeName = queryTypeName(discoveryType)
            JpaQueryType(
               discoveryType,
               classMap[queryTypeName] ?: error("Type $queryTypeName is not mapped to a JPA entity")
            )
         }
      }

      fun queryTypeName(discoveryType: DiscoveryType): QualifiedName {
         return if (Arrays.isArray(discoveryType.type)) {
            discoveryType.type.parameters[0]
         } else {
            discoveryType.type
         }
      }
   }

   val queryTypeName = queryTypeName(discoveryType)
}

