package lang.taxi.generators.java

import lang.taxi.services.Service
import lang.taxi.types.Type
import java.lang.reflect.Method

interface TaxiGeneratorExtension {
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
) : TaxiGeneratorExtension

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
