package lang.taxi.query

import lang.taxi.services.Service
import lang.taxi.services.ServiceMember
import lang.taxi.types.QualifiedName

/**
 * A reference to a service or operation that
 * has been restricted (either included or excluded from a query)
 */
data class ServiceRestriction(
   val service: Service,
   /**
    * The specific members include/excluded.
    * If empty, then the restriction applies to the full service
    */
   val members: List<ServiceMember>
)

data class ServiceRestrictions(
   val inclusions: List<ServiceRestriction>,
   val exclusions: List<ServiceRestriction>
) {
   val isEmpty = inclusions.isEmpty() && exclusions.isEmpty()
   val hasInclusions = inclusions.isNotEmpty()
   val hasExclusions = exclusions.isNotEmpty()

   fun restrictionFor(name: QualifiedName): ServiceRestriction? {
      return inclusions.firstOrNull { it.service.toQualifiedName() == name }
         ?: exclusions.firstOrNull { it.service.toQualifiedName() == name }
   }
   fun inclusionFor(name: QualifiedName): ServiceRestriction? {
      return inclusions.firstOrNull { it.service.toQualifiedName() == name }
   }
   fun exclusionFor(name: QualifiedName): ServiceRestriction? {
      return exclusions.firstOrNull { it.service.toQualifiedName() == name }
   }

   companion object {
      val EMPTY = ServiceRestrictions(emptyList(), emptyList())
   }
}
