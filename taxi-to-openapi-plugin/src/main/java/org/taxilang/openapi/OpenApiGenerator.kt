package org.taxilang.openapi

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.swagger.v3.core.util.Yaml31
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import lang.taxi.TaxiDocument
import lang.taxi.annotations.HttpOperation
import lang.taxi.annotations.HttpService
import lang.taxi.generators.SimpleWriteableSource
import lang.taxi.generators.WritableSource
import lang.taxi.generators.openApi.v3.TaxiExtension
import lang.taxi.query.TaxiQlQuery
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.*
import java.nio.file.Path

class OpenApiGenerator {
   companion object {
      fun matchesNamesFilter(name: String, serviceNamesOrNamespaces: List<String>): Boolean {
         return if (serviceNamesOrNamespaces.isEmpty()) {
            true
         } else {
            serviceNamesOrNamespaces.any { serviceNameOrNamespace -> name.startsWith(serviceNameOrNamespace) }
         }
      }
   }

   fun generateOpenApiSpec(
      taxi: TaxiDocument,
      serviceNamesOrNamespaces: List<String> = emptyList()
   ): List<Pair<QualifiedName, OpenAPI>> {

      val generatedServices = taxi.services
         .filter { service -> service.operations.any { operation -> operation.annotation(HttpOperation.NAME) != null } }
         .filter { service -> matchesNamesFilter(service.qualifiedName, serviceNamesOrNamespaces) }
         .map { service -> service.toQualifiedName() to createOpenApiSpec(service) }

      val generatedQueries = taxi.queries
         .filter { query -> query.annotation(HttpOperation.NAME) != null }
         .filter { query -> matchesNamesFilter(query.name.fullyQualifiedName, serviceNamesOrNamespaces) }
         .map { query -> query.name to createOpenApiSpec(query) }

      return generatedServices + generatedQueries
   }

   fun generateOpenApiSpecAsYaml(
      taxi: TaxiDocument,
      serviceNamesOrNamespaces: List<String> = emptyList()
   ): List<WritableSource> {
      val generated: List<Pair<QualifiedName, OpenAPI>> = generateOpenApiSpec(taxi, serviceNamesOrNamespaces)
      return generateYaml(generated)
   }

   fun generateYaml(
     generatedSpecs: List<Pair<QualifiedName, OpenAPI>>
   ): List<SimpleWriteableSource> {
      return generatedSpecs
         .map { (serviceName, openApi) ->
            val oasSpecName = "${serviceName.typeName}.yaml"
            val yaml = Yaml31.pretty().writeValueAsString(openApi)
            SimpleWriteableSource(Path.of(oasSpecName), yaml)
         }
   }

   private fun createOpenApiSpec(query: TaxiQlQuery, version: String = "1.0.0"): OpenAPI {

      val openApi = openAPI(version, query.name)
      query.annotation(HttpService.NAME)?.let { annotation ->
         val httpService = HttpService.fromAnnotation(annotation)
         openApi.addServersItem(Server().url(httpService.baseUrl))
      }
      val components = Components()
         .schemas(mutableMapOf())

      val paths = Paths()
      appendPathItem(query, paths, components)

      openApi.paths(paths)
         .components(components)
      return openApi
   }

   private fun createOpenApiSpec(service: Service, version: String = "1.0.0"): OpenAPI {

      val openApi = openAPI(version, service.toQualifiedName())
      service.annotation(HttpService.NAME)?.let { annotation ->
         val httpService = HttpService.fromAnnotation(annotation)
         openApi.addServersItem(Server().url(httpService.baseUrl))
      }
      val components = Components()
         .schemas(mutableMapOf())

      val paths = Paths()
      service.operations
         .filter { it.annotation(HttpOperation.NAME) != null }
         .forEach { taxiOperation ->
            appendPathItem(taxiOperation, paths, components)
         }
      openApi.paths(paths)
         .components(components)
      return openApi
   }

   private fun openAPI(version: String, serviceName: QualifiedName): OpenAPI {
      return OpenAPI()
         .info(
            Info()
               .version(version)
               .title(serviceName.typeName)
         )
   }

   private fun appendPathItem(
      query: TaxiQlQuery,
      paths: Paths,
      components: Components,
   ) {
      val returnType = query.returnType
      val responseSchema = typeAsSchema(returnType, components)
      val httpOperation = HttpOperation.fromAnnotation(query.annotation(HttpOperation.NAME)!!)

      val oasOperation = operationBuilder(query.typeDoc, responseSchema)
         .requestBody(buildRequestBodyForQueryParams(query.parameters, components))
         .parameters(query.parameters.filter { it.annotation("PathVariable") != null }
            .map { queryParam ->
               Parameter()
                  .name(queryParam.name)
                  // .description(queryParam.typedoc) // TODO
                  //.required(!queryParam.nullable) // TODO
                  .`in`(getParameterSource(queryParam))
                  .schemaOrRef(typeAsSchema(queryParam.type, components))
            }

         )

      val url = httpOperation.url
      val pathItem = paths[url] ?: PathItem()
      pathItem.operation(PathItem.HttpMethod.valueOf(httpOperation.method.uppercase()), oasOperation)
      paths[url] = pathItem
   }

   private fun buildRequestBodyForQueryParams(parameters: List<lang.taxi.query.Parameter>, components: Components): RequestBody? {
      val parameter = parameters.firstOrNull { it.annotation("RequestBody") != null } ?: return null
      return buildRequestBody(parameter.type, components)
   }

   private fun appendPathItem(
      operation: Operation,
      paths: Paths,
      components: Components,
   ) {
      val responseSchema = typeAsSchema(operation.returnType, components)

      val httpOperation = HttpOperation.fromAnnotation(operation.annotation(HttpOperation.NAME)!!)
      val oasOperation = operationBuilder(operation.typeDoc, responseSchema)
         .requestBody(buildRequestBody(operation.parameters, components))
         .parameters(operation.parameters
            .filter { it.annotation("PathVariable") != null }
            .map { parameter ->
               Parameter()
                  .name(parameter.name)
                  .description(parameter.typeDoc)
                  .required(!parameter.nullable)
                  .`in`(getParameterSource(parameter))
                  .schemaOrRef(typeAsSchema(parameter.type, components))
            })
      val url = httpOperation.url
      val pathItem = paths[url] ?: PathItem()
      pathItem.operation(PathItem.HttpMethod.valueOf(httpOperation.method.uppercase()), oasOperation)
      paths[url] = pathItem
   }

   private fun operationBuilder(
      typeDoc: String?,
      responseSchema: Either<Schema<*>, SchemaRef>
   ): io.swagger.v3.oas.models.Operation {
      return io.swagger.v3.oas.models.Operation()
         .summary(typeDoc)
         .responses(
            ApiResponses()
               .addApiResponse(
                  "200", ApiResponse()
                     .description(typeDoc)
                     .content(
                        Content()
                           .addMediaType(
                              "application/json", MediaType().schema(
                                 responseSchema.wrapRefToObjectSchema()
                              )
                           )
                     )
               )
         );
   }

   private fun buildRequestBody(parameters: List<lang.taxi.services.Parameter>, components: Components): RequestBody? {
      val requestBodyParam = parameters.firstOrNull { it.annotation("RequestBody") != null } ?: return null
      return buildRequestBody(requestBodyParam.type, components)
   }

   private fun buildRequestBody(type: Type, components: Components): RequestBody? {
      return RequestBody()
         .content(
            Content()
               .addMediaType(
                  "application/json",
                  MediaType().schema(typeAsSchema(type, components).wrapRefToObjectSchema())
               )
         )
   }

   private fun getParameterSource(parameter: Annotatable): String {
      return when {
         parameter.annotation("PathVariable") != null -> "path"
         // TODO
         else -> "unknown"
      }
   }

   private fun typeAsSchema(type: Type, components: Components): Either<Schema<*>, SchemaRef> {
      return when (type) {
         is ArrayType -> {
            val memberType = typeAsSchema(type.memberType, components)
            ArraySchema()
               .items(memberType.wrapRefToObjectSchema())
               .left()
         }

         is ObjectType -> {
            if (type.fields.isEmpty() && type.inheritsFromPrimitive) {
               mapAsSemanticScalar(type, components).left()
            } else {
               objectTypeToSchema(type, components).right()
            }
         }

         else -> error("Unhandled branch in mapping type to schema")
      }
   }

   private fun objectTypeToSchema(type: ObjectType, components: Components): SchemaRef {

      val properties = type.fields.associate { field ->
         field.name to typeAsSchema(field.type, components).wrapRefToObjectSchema()
      }
      components.schemas[type.qualifiedName] = ObjectSchema()
         .properties(properties)
         .type("object")

      return "#/components/schemas/${type.qualifiedName}"
   }

   private fun mapAsSemanticScalar(type: ObjectType, components: Components): Schema<*> {
      val schema = when (val baseType = type.basePrimitive!!) {
         PrimitiveType.BOOLEAN -> BooleanSchema()
         PrimitiveType.STRING -> StringSchema()
         PrimitiveType.INTEGER -> IntegerSchema()
         PrimitiveType.DECIMAL -> NumberSchema() // TODO...
         PrimitiveType.LOCAL_DATE -> DateSchema().format(PrimitiveType.LOCAL_DATE.format!!.single())
         PrimitiveType.TIME -> TODO("How are times supposed to be mapped in OAS?")
         PrimitiveType.DATE_TIME -> DateTimeSchema().format(PrimitiveType.LOCAL_DATE.format!!.single())
         PrimitiveType.INSTANT -> DateTimeSchema().format(PrimitiveType.INSTANT.format!!.single())
         PrimitiveType.DOUBLE -> NumberSchema()
         else -> error("Unhandled primitive type ${baseType.name}")
      }
      schema.addExtension(
         TaxiExtension.extensionKey, TaxiExtension(
            type.qualifiedName,
            create = false
         )
      )
      return schema
   }

}

typealias SchemaRef = String
private typealias SchemaOrSchemaRef = Either<Schema<*>, SchemaRef>

private fun Parameter.schemaOrRef(schemaOrRef: SchemaOrSchemaRef): Parameter {
   return when (schemaOrRef) {
      is Either.Left -> this.schema(schemaOrRef.value)
      is Either.Right -> this.`$ref`(schemaOrRef.value)
   }
}


private fun SchemaOrSchemaRef.requireIsRef(): SchemaRef {
   return when (this) {
      is Either.Left -> error("Required a \$ref here, not a schema.")
      is Either.Right -> this.value
   }
}

private fun SchemaOrSchemaRef.wrapRefToObjectSchema(): Schema<*> {
   return when (this) {
      is Either.Left -> this.value
      is Either.Right -> Schema<String>()
         .type(null)
         .`$ref`(this.value)
   }
}
