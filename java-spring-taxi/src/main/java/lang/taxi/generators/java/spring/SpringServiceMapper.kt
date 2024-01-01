package lang.taxi.generators.java.spring

import lang.taxi.TypeNames
import lang.taxi.annotations.HttpOperation
import lang.taxi.annotations.HttpRequestBody
import lang.taxi.generators.java.*
import lang.taxi.services.Operation
import lang.taxi.services.OperationScope
import lang.taxi.services.Service
import lang.taxi.types.Arrays
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Type
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.http.ResponseEntity
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.KType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.javaType
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
      }.filter { method -> !isExcluded(method) }


   .map { method ->
         val requestMappingInfo = SpringMetadataProvider.getSpringMethodMetadata(method, javaClass)
         val url = getUrlForMethod(method, javaClass, typeMapper, mappedTypes, requestMappingInfo)
         val bodyParamIndex = findBodyParamIndex(method)
         val func = method.kotlinFunction
            ?: TODO("I refactored this to use Kotlin functions, must've broken some scenarios where there are no kotlin functions - maybe java?")

         val params: List<lang.taxi.services.Parameter> = method.parameters.mapIndexed { index, param ->
            val kotlinParameter = func.valueParameters[index]
            val paramType =
               typeMapper.getTaxiType(KTypeWrapper(kotlinParameter.type, param), mappedTypes, namespace, null)
            val paramAnnotation = param.getAnnotation(lang.taxi.annotations.Parameter::class.java)
            val annotations = if (index == bodyParamIndex) {
               listOf(HttpRequestBody.toAnnotation())
            } else emptyList()
            lang.taxi.services.Parameter(
               annotations = annotations,
               type = paramType,
               name = paramAnnotation?.name?.orDefaultNullable(kotlinParameter.name) ?: kotlinParameter.name ?: "p$index",
               constraints = emptyList()
            )
         }

         val (methodReturnType, isArrayWrapper) = unwrapSpecialResponseTypeWrappers(func.returnType)
         val returnType = typeMapper.getTaxiType(KTypeWrapper(methodReturnType), mappedTypes, namespace, method)
            .let { type ->
               if (isArrayWrapper) {
                  Arrays.arrayOf(type)
               } else {
                  type
               }
            }


         Operation(
            method.name,
            parameters = params,
            annotations = listOf(
               HttpOperation(
                  requestMappingInfo.requestMethod.name,
                  url
               ).toAnnotation()
            ),
            returnType = returnType,
            typeDoc = method.findTypeDoc(),
            scope = OperationScope.READ_ONLY, // TODO

            compilationUnits = listOf(CompilationUnit.Companion.generatedFor("${javaClass.name}::${method.name}"))
         )
      }
      return setOf(
         Service(
            serviceName,
            operations,
            annotations = emptyList(),
            typeDoc = javaClass.findTypeDoc(),
            compilationUnits = listOf(CompilationUnit.generatedFor(javaClass.name))
         )
      )
   }

   private fun isExcluded(method: Method?): Boolean {
      if (method == null) return true
      val operationAnnotation = method.getAnnotation(lang.taxi.annotations.Operation::class.java)
         ?: return false
      return operationAnnotation.excluded
   }


   @OptIn(ExperimentalStdlibApi::class)
   private fun unwrapSpecialResponseTypeWrappers(returnType: KType): Pair<KType, IsWrapperTypeForArray> {
      when (returnType.classifier) {
         ResponseEntity::class -> {
            return returnType.arguments.first().type!! to false
         }

         Flux::class -> {
            return returnType.arguments.first().type!! to true
         }

         Mono::class -> {
            return returnType.arguments.first().type!! to false
         }
      }
      // Using class name here, to avoid pulling in Coroutines as a compile time dependency
      if (returnType.javaType.typeName.startsWith("kotlinx.coroutines.flow.Flow")) {
         return returnType.arguments.first().type!! to true
      }
      // Not a known wrapper, just return the provided type
      return returnType to false
   }

   private fun getUrlForMethod(
      method: Method,
      type: Class<*>,
      typeMapper: TypeMapper,
      mappedTypes: MutableSet<Type>,
      requestMappingInfo: SpringMethodMetadata
   ): String {

      var url = baseUrl.joinEnsuringOnlyOneSeperator(requestMappingInfo.url, "/")
      val pathVariablesAndIndices = findPathVariablesToParams(url, method)
      val func = method.kotlinFunction!!

      pathVariablesAndIndices.map { (name, parameter) ->
         // defaultNamespace was:
         //   typeMapper.getTaxiType(type,mappedTypes).toQualifiedName().namespace
         val defaultNamespace = TypeNames.deriveNamespace(type)
         val paramIndex = method.parameters.indexOf(parameter)
         val kotlinParameter = func.valueParameters[paramIndex]
         val paramTaxiType =
            typeMapper.getTaxiType(KTypeWrapper(kotlinParameter.type, parameter), mappedTypes, defaultNamespace, null)

//         val paramTaxiType = typeMapper.getTaxiType(parameter, mappedTypes, defaultNamespace, null)
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

private typealias IsWrapperTypeForArray = Boolean
