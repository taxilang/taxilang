package lang.taxi

import com.google.common.collect.Multimaps
import lang.taxi.policies.Policy
import lang.taxi.services.Service
import lang.taxi.types.*


fun TaxiParser.QualifiedNameContext.toAttributePath(): AttributePath {
   return AttributePath(this.Identifier().map { it.text })
}

class NamespacedTaxiDocument(val namespace: String,
                             types: Set<Type>,
                             services: Set<Service>,
                             policies: Set<Policy>,
                             sources: Set<DataSource>) : TaxiDocument(types, services, policies, sources)

// Note:  Changed types & services from List<> to Set<>
// as ordering shouldn't matter, only content.
// However, I suspect there was a reason these were Lists, so leaving this note here to remind me
open class TaxiDocument(val types: Set<Type>,
                        val services: Set<Service>,
                        val policies: Set<Policy> = emptySet(),
                        val dataSources: Set<DataSource> = emptySet()
) {
   private val equality = Equality(this, TaxiDocument::types, TaxiDocument::services)
   private val typeMap = types.associateBy { it.qualifiedName }
   private val servicesMap = services.associateBy { it.qualifiedName }
   private val policiesMap = policies.associateBy { it.qualifiedName }
   private val dataSourcesMap = dataSources.associateBy { it.qualifiedName }
   fun type(name: String): Type {
      return typeMap[name] ?: throw error("No type named $name defined")
   }

   // This is a placeholder for when we start to seperate models and types
   fun model(name: String) = objectType(name)

   fun containsType(typeName: String) = typeMap.containsKey(typeName)
   fun containsService(serviceName: String) = servicesMap.containsKey(serviceName)

   override fun hashCode() = equality.hash()
   override fun equals(other: Any?) = equality.isEqualTo(other)

   fun toNamespacedDocs(): List<NamespacedTaxiDocument> {
      val typesByNamespace = Multimaps.index(types) { it!!.toQualifiedName().namespace }
      val servicesByNamespace = Multimaps.index(services) { it!!.toQualifiedName().namespace }
      val policiesByNamespace = Multimaps.index(policies) { it!!.toQualifiedName().namespace }
      val sourcesByNamespace = Multimaps.index(dataSources) { it!!.toQualifiedName().namespace }
      val namespaces = typesByNamespace.keySet() + servicesByNamespace.keySet()

      return namespaces.map { namespace ->
         NamespacedTaxiDocument(namespace,
            types = typesByNamespace.get(namespace)?.toSet() ?: emptySet(),
            services = servicesByNamespace.get(namespace)?.toSet() ?: emptySet(),
            policies = policiesByNamespace.get(namespace)?.toSet() ?: emptySet(),
            sources = sourcesByNamespace.get(namespace)?.toSet() ?: emptySet()
         )
      }
   }

   fun objectType(name: String): ObjectType {
      return type(name) as ObjectType
   }

   fun typeAlias(name: String): TypeAlias {
      return type(name) as TypeAlias
   }

   fun enumType(qualifiedName: String): EnumType {
      return type(qualifiedName) as EnumType
   }

   fun service(qualifiedName: String): Service {
      return servicesMap[qualifiedName]!!
   }

   fun policy(qualifiedName: String): Policy {
      return policiesMap[qualifiedName]!!
   }

   fun containsPolicy(qualifiedName: String): Boolean {
      return policiesMap.containsKey(qualifiedName)
   }


   private fun Iterable<CompilationUnit>.declarationSites(): String {
      return this.joinToString { it.source.sourceName }
   }

   fun merge(other: TaxiDocument): TaxiDocument {
      val conflicts: List<Named> = collectConflictingTypes(other) + collectDuplicateServices(other)
      val errors = conflicts.map {
         val site1 = this.type(it.qualifiedName).compilationUnits.declarationSites()
         val site2 = other.type(it.qualifiedName).compilationUnits.declarationSites()
         DocumentStrucutreError("Attempted to redefine types with conflicting definition - ${it.qualifiedName} is defined in the following locations: $site1 which conflicts with the definition at $site2")
      }
      if (errors.isNotEmpty()) {
         throw DocumentMalformedException(errors)
      }

      // TODO : We should be merging where there are extensions in otherwise
      // equal type definitions.
      val duplicateNames = this.types.filter { other.containsType(it.qualifiedName) }.map { it.qualifiedName }

      return TaxiDocument(this.types + other.types.filterNot { duplicateNames.contains(it.qualifiedName) },
         this.services + other.services,
         this.policies + other.policies,
         this.dataSources + other.dataSources
      )
   }

   private fun collectDuplicateServices(other: TaxiDocument): List<Service> {
      val duplicateServices = this.services.filter { other.containsService(it.qualifiedName) }
      return duplicateServices.filter { it != other.service(it.qualifiedName) }

   }

   private fun collectConflictingTypes(other: TaxiDocument): List<Type> {
      val duplicateTypes = this.types.filter { other.containsType(it.qualifiedName) }
      // TODO : This should consider extensions.
      // If the underlying type definitions are the same, but one adds extensions,
      // that's valid.
      return duplicateTypes.filter { it != other.type(it.qualifiedName) }

   }

   fun dataSource(name: String): DataSource {
      return dataSourcesMap[name] ?: error("Datasource $name is not defined")
   }

}
