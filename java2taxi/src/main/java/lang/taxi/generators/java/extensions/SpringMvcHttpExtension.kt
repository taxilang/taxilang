package lang.taxi.generators.java.extensions

import lang.taxi.Type
import lang.taxi.TypeNames
import lang.taxi.annotations.HttpOperation
import lang.taxi.annotations.HttpRequestBody
import lang.taxi.generators.java.OperationMapperExtension
import lang.taxi.generators.java.ServiceMapperExtension
import lang.taxi.generators.java.TypeMapper
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.Annotation
import lang.taxi.types.AnnotationProvider
import org.springframework.cloud.netflix.feign.support.SpringMvcContract
import java.lang.reflect.Method

interface HttpServiceAddressProvider : AnnotationProvider {

}

class ServiceDiscoveryAddressProvider(private val applicationName:String):HttpServiceAddressProvider {
    override fun toAnnotation(): Annotation {
        return lang.taxi.types.Annotation("ServiceDiscoveryClient", mapOf("serviceName" to applicationName))
    }
}

class SpringMvcHttpServiceExtension(val addressProvider: HttpServiceAddressProvider) : ServiceMapperExtension {
    override fun update(service: Service, type: Class<*>, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Service {
        return service.copy(annotations = service.annotations + addressProvider.toAnnotation())
    }
}

class SpringMvcHttpOperationExtension(val springMvcContract: SpringMvcContract = SpringMvcContract()) : OperationMapperExtension {
    override fun update(operation: Operation, type: Class<*>, method: Method, typeMapper: TypeMapper, mappedTypes: MutableSet<Type>): Operation {
        // Note: i'm using Feign here for parsing, as it seems like a good normalizer - a wide range of support of different frameworks
        // which should all be reesolvable back to Feign's library.  If this turns out not to be true, revisit this decision
        val metadata = springMvcContract.parseAndValidateMetadata(type, method)
        var url = metadata.template().url()
        metadata.indexToName().forEach { index, names ->
            // replace the placeholder in the url (which uses param names) with types.
            // TODO : This is a naieve first impl.  Need to make use of param names here sensibly, and inline with
            // the principal of favouring descriptive types over parameter names
            val parameter = method.parameters[index]
            // defaultNamespace was:
            //   typeMapper.getTaxiType(type,mappedTypes).toQualifiedName().namespace
            val defaultNamespace = TypeNames.deriveNamespace(type)
            val paramTaxiType = typeMapper.getTaxiType(parameter, mappedTypes, defaultNamespace, method)
            names.forEach { name -> url = url.replace("{$name}", "{${paramTaxiType.qualifiedName}}") }
        }

        val updatedParams = if (metadata.bodyIndex() != null) {
            val originalParam = operation.parameters[metadata.bodyIndex()]
            val paramWithAnnotation = originalParam.copy(annotations = originalParam.annotations + HttpRequestBody.toAnnotation())
            // Add the @RequestBody annotation
            operation.parameters.replacing(metadata.bodyIndex(), paramWithAnnotation)

        } else operation.parameters
        return operation.copy(annotations = operation.annotations + HttpOperation(metadata.template().method(), url).toAnnotation(),
                parameters = updatedParams)
    }
}

fun <T> List<T>.replacing(index: Int, value: T): List<T> {
    val copy = this.toMutableList()
    copy.set(index, value)
    return copy.toList()
}
