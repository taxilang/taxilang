package lang.taxi.generators.openApi.v3

import io.swagger.oas.models.OpenAPI
import io.swagger.oas.models.Operation
import io.swagger.oas.models.PathItem
import io.swagger.oas.models.parameters.Parameter
import io.swagger.oas.models.parameters.QueryParameter
import io.swagger.oas.models.responses.ApiResponse
import lang.taxi.CompilationUnit
import lang.taxi.Type
import lang.taxi.annotations.HttpOperation
import lang.taxi.generators.Logger
import lang.taxi.generators.openApi.OperationIdProvider
import lang.taxi.services.Constraint
import lang.taxi.services.Service
import lang.taxi.types.Annotation
import lang.taxi.types.PrimitiveType
import lang.taxi.types.VoidType
import lang.taxi.types.toAnnotations
import v2.io.swagger.models.parameters.BodyParameter

class OpenApiServiceMapper(private val openAPI: OpenAPI,
                           private val typeGenerator: OpenApiTypeMapper,
                           private val logger: Logger) {
    fun generateServices(): Set<Service> {
        val basePath = openAPI.servers.firstOrNull()?.url
        if (basePath == null) {
            logger.error("No URL provided in the set of servers")
            return emptySet()
        }
        val services = openAPI.paths.map { (pathMapping, pathOperation) ->
            val qualifiedPath = "$basePath/$pathMapping".replace("//", "/")
            generateService(qualifiedPath, pathOperation, pathMapping)
        }.toSet()
        return services
    }

    private fun generateService(qualifiedPath: String, pathOperation: PathItem, pathMapping: String): Service {
        val operations = pathOperation.readOperationsMap()
                .filter { canSupport(it.value) }
                .map { (method, operation) ->
                    generateOperation(method, operation, pathMapping)
                }
        val derivedServiceName = pathMapping.split("/")
                .joinToString(separator = "") { it.removeSurrounding("{", "}").capitalize() } + "Service"
        val qualifiedServiceName = "${typeGenerator.defaultNamespace}.$derivedServiceName"
        return Service(
                qualifiedServiceName,
                operations,
                annotations = emptyList(),
                compilationUnits = emptyList()
        )
    }

    private fun generateOperation(method: PathItem.HttpMethod, openApiOperation: Operation, pathMapping: String): lang.taxi.services.Operation {
        val annotations = listOf(
                HttpOperation(method = method.name, url = pathMapping)
        )
        val swaggerParams = openApiOperation.parameters ?: emptyList()

        val parameters = swaggerParams.map { swaggerParam ->
            val annotations = getParamAnnotations(swaggerParam)
            val type = getParamType(swaggerParam)
            val constraints = emptyList<Constraint>()
            lang.taxi.services.Parameter(annotations, type, swaggerParam.name, constraints)
        }
        val operationId = OperationIdProvider.getOperationId(openApiOperation, pathMapping, method)
        val returnType = getReturnType(openApiOperation, operationId)

        return lang.taxi.services.Operation(
                operationId,
                annotations.toAnnotations(),
                parameters,
                returnType,
                listOf(CompilationUnit.unspecified())
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
        return typeGenerator.findType(mediaType.schema, operationId)
    }

    private fun getParamType(swaggerParam: Parameter): Type {
        return typeGenerator.findType(swaggerParam.schema)
    }

    private fun getParamAnnotations(param: Parameter): List<Annotation> {
        return when (param) {
            is BodyParameter -> TODO()
            is QueryParameter -> {
                logger.warn("Param Query parameters are not currently supported", "https://gitlab.com/taxi-lang/taxi-lang/issues/21")
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun canSupport(operation: Operation): Boolean {
        // TODO
        return true
    }

}