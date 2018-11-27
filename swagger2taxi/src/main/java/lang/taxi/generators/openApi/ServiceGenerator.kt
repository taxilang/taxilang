package lang.taxi.generators.openApi

import lang.taxi.CompilationUnit
import lang.taxi.Type
import lang.taxi.annotations.HttpOperation
import lang.taxi.annotations.HttpRequestBody
import lang.taxi.generators.Logger
import lang.taxi.types.Annotation
import lang.taxi.types.VoidType
import lang.taxi.types.toAnnotations
import v2.io.swagger.models.*
import v2.io.swagger.models.parameters.*
import java.net.URL
import lang.taxi.services.Operation as TaxiOperation
import lang.taxi.services.Service as TaxiService
import v2.io.swagger.models.Operation as SwaggerOperation


class SwaggerServiceGenerator(val swagger: Swagger, val typeMapper: SwaggerTypeMapper, val logger: Logger) {

    private val defaultNamespace: String
        get() {
            return typeMapper.defaultNamespace
        }

    fun generateServices(): Set<TaxiService> {
        val scheme = selectBestScheme()
        val basePath = "$scheme:///${swagger.host}/${swagger.basePath}"
        val services = swagger.paths.map { (pathMapping, swaggerPath) ->
            val path = "$basePath/$pathMapping".replace("//", "/")
            generateService(path, swaggerPath, pathMapping)
        }.toSet()
        return services
    }

    private fun selectBestScheme(): String {
        val orderOfPreference = listOf(
                Scheme.HTTPS,
                Scheme.HTTP,
                Scheme.WSS,
                Scheme.WS
        )
        val scheme = orderOfPreference.firstOrNull { swagger.schemes.contains(it) } ?: error("No valid scheme found")
        return scheme.toValue()

    }

    fun generateService(fullPath: String, swaggerPath: Path, relativePath: String): TaxiService {
        val operations = swaggerPath.operationMap
                .filter { canSupport(it.value) }
                .map { (method, swaggerOperation) -> generateOperation(method, swaggerOperation, fullPath) }
        val derivedServiceName = relativePath.split("/")
                .map { it.removeSurrounding("{", "}").capitalize() }
                .joinToString(separator = "") + "Service"
        val qualifiedServiceName = "${defaultNamespace}.$derivedServiceName"
        return TaxiService(
                qualifiedServiceName,
                operations,
                annotations = emptyList(),
                compilationUnits = emptyList()
        )
    }

    private fun canSupport(operation: v2.io.swagger.models.Operation?): Boolean {
        if (operation == null) return false;
        // We don't support form based submissions yet, but will do later.
        if (operation.parameters.any { it is FormParameter }) {
            logger.warn("Operation ${operation.operationId} has been excluded, as it requires form data, which is not yet supported.  Support is coming soon.", "https://gitlab.com/vyne/vyne/issues/48")
            return false
        }

        if (operation.parameters.any { it is HeaderParameter }) {
            logger.warn("Operation ${operation.operationId} has been excluded, as it requires header-based params, which is not yet supported.  Support is coming soon.", "https://gitlab.com/vyne/vyne/issues/47")
            return false
        }

        return true
    }

    private fun generateOperation(method: HttpMethod, swaggerOperation: SwaggerOperation, pathMapping: String): TaxiOperation {
        // TODO : Annotations ... need to do service discvoery and http method.
        val annotations = listOf(
                HttpOperation(method = method.name, url = pathMapping)
        )
        val parameters = swaggerOperation.parameters.map { swaggerParam ->
            val annotations = getParamAnnotations(swaggerParam)
            val type = getParamType(swaggerParam)
            val constraints = emptyList<lang.taxi.services.Constraint>()
            lang.taxi.services.Parameter(annotations, type, swaggerParam.name, constraints)
        }
        val returnType = getReturnType(swaggerOperation)
        val operationId = OperationIdProvider.getOperationId(swaggerOperation, pathMapping, method)
        return TaxiOperation(
                operationId,
                annotations.toAnnotations(),
                parameters,
                returnType,
                listOf(CompilationUnit.unspecified())
        )

    }


    private fun getReturnType(swaggerOperation: SwaggerOperation): Type {
        // First look for responses in the 200 range:
        val successfulResponses = swaggerOperation.responses.mapNotNull { (key, response) ->
            val responseCode = key.toIntOrNull() ?: return@mapNotNull null;
            if (responseCode in 200..299) {
                responseCode to response
            } else null
        }.toMap().toSortedMap()

        if (successfulResponses.isNotEmpty()) {
            return getReturnType(successfulResponses[successfulResponses.firstKey()]!!)
        } else {
            return VoidType.VOID
//            TODO("No successful response found - not sure what the return type is.")
        }
    }

    private fun getReturnType(response: Response): Type {
        if (response.schema == null) {
            return VoidType.VOID
        }
        return typeMapper.getOrGenerateType(response.schema)
    }

    private fun getParamType(param: Parameter): Type {
        return when (param) {
            is BodyParameter -> typeMapper.findType(param.schema)
            is PathParameter -> typeMapper.findType(param) // TODO : Use format here, to disambiguate Int/long etc
            is QueryParameter -> typeMapper.findType(param)
            else -> TODO("Unhandled Param type : ${param.javaClass.name}")
        }
    }

    private fun getParamAnnotations(param: Parameter): List<Annotation> {
        return when (param) {
            is BodyParameter -> listOf(HttpRequestBody.toAnnotation())
            // TODO : Path variables?
            else -> emptyList()
        }
    }
}

object OperationIdProvider {
    fun getOperationId(operation: v2.io.swagger.models.Operation, pathMapping: String, method: HttpMethod): String {
        if (operation.operationId != null) {
            return operation.operationId
        }


        val path = URL(pathMapping).path.split("/")
        val words = listOf(method.name.toLowerCase()) + path
        return words.joinToString("") { it.capitalize() }

    }
}