package lang.taxi.generators.openApi.v3


import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.PathParameter
import io.swagger.v3.oas.models.parameters.QueryParameter
import io.swagger.v3.oas.models.responses.ApiResponse
import lang.taxi.annotations.HttpOperation
import lang.taxi.annotations.HttpPathVariable
import lang.taxi.annotations.HttpRequestBody
import lang.taxi.generators.Logger
import lang.taxi.generators.NamingUtils.replaceIllegalCharacters
import lang.taxi.generators.openApi.OperationIdProvider
import lang.taxi.services.OperationScope
import lang.taxi.services.Service
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.*
import lang.taxi.types.Annotation

class OpenApiServiceMapper(
   private val openAPI: OpenAPI,
   private val typeGenerator: OpenApiTypeMapper,
   private val logger: Logger
) {
   fun generateServices(basePath: String? = null): Set<Service> {
      val basePathFromSwagger = openAPI.servers?.firstOrNull()?.url
      val basePathToUse = listOfNotNull(basePath, basePathFromSwagger).firstOrNull()
      if (basePathToUse == null) {
         logger.error("No URL provided in the set of servers, and no manual basePath provided")
         return emptySet()
      }
      val services = openAPI.paths.map { (pathMapping, pathOperation) ->
         generateService(basePathToUse, pathOperation, pathMapping)
      }.toSet()
      return services
   }

   private fun generateService(basePathToUse: String, pathOperation: PathItem, pathMapping: String): Service {
      val operations = pathOperation.readOperationsMap()
         .filter { canSupport(it.value) }
         .map { (method, operation) ->
            generateOperation(
               method,
               operation,
               (basePathToUse.removeSuffix("/") + "/" + pathMapping.removePrefix("/"))
            )
         }
      val derivedServiceName = pathMapping.split("/")
         .joinToString(separator = "") {
            it.removeSurrounding("{", "}").replaceIllegalCharacters().capitalize()
         } + "Service"
      val qualifiedServiceName = "${typeGenerator.defaultNamespace}.$derivedServiceName"
      return Service(
         qualifiedServiceName,
         operations,
         annotations = emptyList(),
         compilationUnits = emptyList(),
         typeDoc = pathOperation.description,
      )
   }

   private fun generateOperation(
      method: PathItem.HttpMethod,
      openApiOperation: Operation,
      pathMapping: String
   ): lang.taxi.services.Operation {
      val annotations = listOf(
         HttpOperation(method = method.name, url = pathMapping)
      )
      val swaggerParams = openApiOperation.parameters ?: emptyList()

      val parameters = swaggerParams.map { swaggerParam ->
         val annotations = getParamAnnotations(swaggerParam)
         val type = getParamType(swaggerParam)
         val taxiDoc: String? = swaggerParam.description ?: swaggerParam.schema.description
         val constraints = emptyList<Constraint>()
         lang.taxi.services.Parameter(
            annotations,
            type,
            swaggerParam.name.replaceIllegalCharacters(),
            constraints,
            typeDoc = taxiDoc
         )
      }
      val operationId = OperationIdProvider.getOperationId(openApiOperation, pathMapping, method)
      val requestBodyParam =
         openApiOperation.requestBody?.content?.values?.firstOrNull()?.schema?.let { requestBodySchema ->
            val type = typeGenerator.generateUnnamedTypeRecursively(requestBodySchema, operationId + "Body")
            lang.taxi.services.Parameter(
               annotations = listOf(HttpRequestBody.toAnnotation()),
               type = type,
               name = type.toQualifiedName().typeName.decapitalize(),
               constraints = emptyList(),
            )
         }
      val returnType = getReturnType(openApiOperation, operationId)

      return lang.taxi.services.Operation(
         operationId,
         OperationScope.READ_ONLY, // scope - TODO
         annotations.toAnnotations(),
         parameters + listOfNotNull(requestBodyParam),
         returnType,
         listOf(CompilationUnit.unspecified()),
         typeDoc = openApiOperation.description,
      )
   }

   private fun getReturnType(openApiOperation: Operation, operationId: String): Type {
      val successfulResponses = openApiOperation.responses.mapNotNull { (responseCodeStr, response) ->
         val responseCode = responseCodeStr.toIntOrNull() ?: return@mapNotNull null
         if (responseCode in 200..299) {
            responseCode to response
         } else null
      }.toMap().toSortedMap()

      return if (successfulResponses.isNotEmpty()) {
         getReturnType(successfulResponses[successfulResponses.firstKey()]!!, operationId)
      } else {
         VoidType.VOID
      }
   }

   private fun getReturnType(response: ApiResponse, operationId: String): Type {
      if (response.content == null) {
         return VoidType.VOID
      }
      val mediaType = response.content.entries.first().value
      if (mediaType.schema == null) {
         if (mediaType.examples != null && mediaType.examples.isNotEmpty()) {
            return PrimitiveType.ANY
         }
         if (mediaType.example != null) {
            return PrimitiveType.ANY
         }
      }
      return typeGenerator.generateUnnamedTypeRecursively(mediaType.schema, operationId)
   }

   private fun getParamType(swaggerParam: Parameter): Type {
      if (swaggerParam.`$ref` != null) {
         TODO()
      } else {
         return typeGenerator.generateUnnamedTypeRecursively(swaggerParam.schema, swaggerParam.name)
      }


   }

   private fun getParamAnnotations(param: Parameter): List<Annotation> {
      return when (param) {
         is QueryParameter -> {
            logger.warn(
               "Param Query parameters are not currently supported",
               "https://gitlab.com/taxi-lang/taxi-lang/issues/21"
            )
            emptyList()
         }

         is PathParameter -> listOf(HttpPathVariable(param.name).toAnnotation())
         else -> emptyList()
      }
   }

   private fun canSupport(operation: Operation): Boolean {
      // TODO
      return true
   }

}
