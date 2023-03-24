package lang.taxi.generators.java

import lang.taxi.services.Service
import lang.taxi.types.Type
import org.reflections8.Reflections
import java.lang.reflect.Method

interface TaxiGeneratorExtension {

   fun getClassesToScan(reflections: Reflections, packageName: String): List<Class<*>> = emptyList()

   /**
    * A predicate that runs over the set of classes provided to the generator.
    * Returns true if the class exposes services that we should generate
    */
   val isServiceType: (Class<*>) -> Boolean
      get() = { false }

   val shouldGenerateTaxiType: (Class<*>) -> Boolean
      get() = { false }

   val serviceMapperExtensions: List<ServiceMapperExtension>
      get() {
         return emptyList()
      }
   val operationMapperExtensions: List<OperationMapperExtension>
      get() {
         return emptyList()
      }
}

data class DefaultTaxiGeneratorExtension(
   val name: String,
   override val serviceMapperExtensions: List<ServiceMapperExtension>,
   override val operationMapperExtensions: List<OperationMapperExtension>,
) : TaxiGeneratorExtension {

}

interface ServiceMapperExtension {
   fun update(service: Service, type: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Service
}

interface OperationMapperExtension {
   fun update(
      operation: lang.taxi.services.Operation,
      type: Class<*>,
      method: Method,
      typeMapper: TypeMapper,
      mappedTypes: MutableSet<Type>
   ): lang.taxi.services.Operation
}
