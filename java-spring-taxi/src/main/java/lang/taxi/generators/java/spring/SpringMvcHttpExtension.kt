package lang.taxi.generators.java.spring

import lang.taxi.TypeNames
import lang.taxi.annotations.HttpOperation
import lang.taxi.annotations.HttpRequestBody
import lang.taxi.generators.java.DefaultTaxiGeneratorExtension
import lang.taxi.generators.java.OperationMapperExtension
import lang.taxi.generators.java.TaxiGeneratorExtension
import lang.taxi.generators.java.TypeMapper
import lang.taxi.services.Operation
import lang.taxi.types.Type
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.Method
import java.lang.reflect.Parameter

object SpringMvcExtension {
   fun forBaseUrl(baseUrl: String): TaxiGeneratorExtension {
      return DefaultTaxiGeneratorExtension(
         "spring-mvc",
         emptyList(),
         listOf(SpringMvcHttpOperationExtension(baseUrl))
      )
   }

   fun forDiscoveryClient(applicationName: String, useHttps: Boolean = false): TaxiGeneratorExtension {
      val scheme = if (useHttps) {
         "https"
      } else {
         "http"
      }
      return forBaseUrl("$scheme://$applicationName/")
   }
}


class SpringMvcHttpOperationExtension(
   private val baseUrl: String = ""
) : OperationMapperExtension {
   override fun update(
      operation: Operation,
      type: Class<*>,
      method: Method,
      typeMapper: TypeMapper,
      mappedTypes: MutableSet<Type>
   ): Operation {
      val requestMappingInfo = MetadataParser().getMappingForMethod(method, type)
      val methodUrl = findMethodUrl(requestMappingInfo)
      var url = baseUrl.joinEnsuringOnlyOneSeperator(methodUrl, "/")
      val pathVariablesAndIndices = findPathVariablesToParams(url, method)
      pathVariablesAndIndices.map { (name, parameter) ->
         // defaultNamespace was:
         //   typeMapper.getTaxiType(type,mappedTypes).toQualifiedName().namespace
         val defaultNamespace = TypeNames.deriveNamespace(type)
         val paramTaxiType = typeMapper.getTaxiType(parameter, mappedTypes, defaultNamespace, method)
         url = url.replace("{$name}", "{${paramTaxiType.qualifiedName}}")
      }

      val bodyParamIndex = findBodyParamIndex(method)

      val updatedParams = if (bodyParamIndex != -1) {
         val originalParam = operation.parameters[bodyParamIndex]
         val paramWithAnnotation =
            originalParam.copy(annotations = originalParam.annotations + HttpRequestBody.toAnnotation())
         // Add the @RequestBody annotation
         operation.parameters.replacing(bodyParamIndex, paramWithAnnotation)

      } else operation.parameters
      return operation.copy(
         annotations = operation.annotations + HttpOperation(
            requestMappingInfo.methodsCondition.methods.first().name,
            url
         ).toAnnotation(),
         parameters = updatedParams
      )
   }

   private fun findBodyParamIndex(method: Method): Int {
      return method.parameters.indexOfFirst {
         it.isAnnotationPresent(RequestBody::class.java)
      }
   }

   private fun findPathVariablesToParams(url: String, method: Method): Map<String, Parameter> {
      val keys = AntPathMatcher().extractUriTemplateVariables(url, url).keys
      return keys.map { pathVariable ->
         val parameter = method.parameters.first { param ->
            param.isAnnotationPresent(PathVariable::class.java) &&
               AnnotatedElementUtils.findMergedAnnotation(param, PathVariable::class.java).value == pathVariable
         }
         pathVariable to parameter
      }.toMap()
   }

   private fun findMethodUrl(requestMappingInfo: RequestMappingInfo): String {
      require(requestMappingInfo.patternValues.isNotEmpty()) { "Expected to find patternValues populated.  This suggests a usage of Spring annotations we haven't considered" }
      return requestMappingInfo.patternValues.first()
   }
}

fun <T> List<T>.replacing(index: Int, value: T): List<T> {
   val copy = this.toMutableList()
   copy.set(index, value)
   return copy.toList()
}

fun String.joinEnsuringOnlyOneSeperator(other: String, seperator: String): String {
   return this.removeSuffix(seperator) + seperator + other.removePrefix(seperator)
}


class MetadataParser : RequestMappingHandlerMapping() {
   init {
//       super.afterPropertiesSet()
   }

   public override fun getMappingForMethod(method: Method, handlerType: Class<*>): RequestMappingInfo {
      return super.getMappingForMethod(method, handlerType)
   }
}
