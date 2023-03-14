package lang.taxi.generators.java.spring

import lang.taxi.TypeNames
import lang.taxi.annotations.HttpOperation
import lang.taxi.annotations.HttpRequestBody
import lang.taxi.generators.java.KTypeWrapper
import lang.taxi.generators.java.OperationMapperExtension
import lang.taxi.generators.java.ServiceMapper
import lang.taxi.generators.java.ServiceMapperExtension
import lang.taxi.generators.java.TaxiGenerator
import lang.taxi.generators.java.TypeMapper
import lang.taxi.generators.java.deriveServiceName
import lang.taxi.generators.java.findTypeDoc
import lang.taxi.generators.java.orDefaultNullable
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Type
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction


object SpringTaxiGenerator {
   fun forBaseUrl(baseUrl: String): TaxiGenerator {
      return TaxiGenerator(
         serviceMapper = SpringServiceMapper(baseUrl)
      ).addExtension(SpringMvcExtension.forBaseUrl(baseUrl))
   }
}

class SpringServiceMapper(val baseUrl: String) : ServiceMapper {
   override fun getTaxiServices(
      javaClass: Class<*>,
      typeMapper: TypeMapper,
      mappedTypes: MutableSet<Type>
   ): Set<Service> {
      val namespace = TypeNames.deriveNamespace(javaClass)
      val serviceName = deriveServiceName(javaClass, namespace)

      val operations = javaClass.methods.filter { method ->
         AnnotationUtils.findAnnotation(method, RequestMapping::class.java) != null
      }.map { method ->
         val requestMappingInfo = MetadataParser().getMappingForMethod(method, javaClass)
         val url = getUrlForMethod(method, javaClass, typeMapper, mappedTypes, requestMappingInfo)
         val bodyParamIndex = findBodyParamIndex(method)
         val func = method.kotlinFunction
            ?: TODO("I refactored this to use Kotlin functions, must've broken some scenarios where there are no kotlin functions - maybe java?")

         val params: List<lang.taxi.services.Parameter> = method.parameters.mapIndexed { index, param ->
            val kotlinParameter = func.valueParameters[index]
            val paramType = typeMapper.getTaxiType(param, mappedTypes, namespace, method)
            val paramAnnotation = param.getAnnotation(lang.taxi.annotations.Parameter::class.java)
            val annotations = if (index == bodyParamIndex) {
               listOf(HttpRequestBody.toAnnotation())
            } else emptyList()
            lang.taxi.services.Parameter(
               annotations = annotations,
               type = paramType,
               name = paramAnnotation?.name?.orDefaultNullable(kotlinParameter.name) ?: kotlinParameter.name,
               constraints = emptyList()
            )
         }

         val returnType = typeMapper.getTaxiType(KTypeWrapper(func.returnType), mappedTypes, namespace, method)

         Operation(
            method.name,
            parameters = params,
            annotations = listOf(
               HttpOperation(
                  requestMappingInfo.methodsCondition.methods.first().name,
                  url
               ).toAnnotation()
            ),
            returnType = returnType,
            typeDoc = method.findTypeDoc(),
            scope = null,
            compilationUnits = listOf(CompilationUnit.Companion.generatedFor("${javaClass.canonicalName}::${method.name}"))
         )
      }
      return setOf(
         Service(
            serviceName,
            operations,
            annotations = emptyList(),
            typeDoc = javaClass.findTypeDoc(),
            compilationUnits = listOf(CompilationUnit.generatedFor(javaClass.canonicalName))
         )
      )
   }

   private fun getUrlForMethod(
      method: Method,
      type: Class<*>,
      typeMapper: TypeMapper,
      mappedTypes: MutableSet<Type>,
      requestMappingInfo: RequestMappingInfo
   ): String {

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
      return url
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

   private fun findBodyParamIndex(method: Method): Int {
      return method.parameters.indexOfFirst {
         it.isAnnotationPresent(RequestBody::class.java)
      }
   }

   private fun findMethodUrl(requestMappingInfo: RequestMappingInfo): String {
      require(requestMappingInfo.patternValues.isNotEmpty()) { "Expected to find patternValues populated.  This suggests a usage of Spring annotations we haven't considered" }
      return requestMappingInfo.patternValues.first()
   }

   override fun addServiceExtensions(serviceExtensions: List<ServiceMapperExtension>): ServiceMapper {
      return this
   }

   override fun addOperationExtensions(operationExtensions: List<OperationMapperExtension>): ServiceMapper {
      return this
   }
}
