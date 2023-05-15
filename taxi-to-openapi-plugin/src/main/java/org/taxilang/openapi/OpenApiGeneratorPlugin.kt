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
import lang.taxi.generators.*
import lang.taxi.generators.openApi.v3.TaxiExtension
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.ArtifactId
import lang.taxi.plugins.PluginWithConfig
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.*
import java.nio.file.Path

data class OpenApiPluginConfig(
   val foo: String = ""
)

class OpenApiGeneratorPlugin : PluginWithConfig<OpenApiPluginConfig>, ModelGenerator {
   private lateinit var config: OpenApiPluginConfig
   override fun setConfig(config: OpenApiPluginConfig) {
      this.config = config
   }

   override val id: ArtifactId = ArtifactId(Artifact.DEFAULT_GROUP, "open-api")

   override fun generate(
      taxi: TaxiDocument,
      processors: List<Processor>,
      environment: TaxiProjectEnvironment
   ): List<WritableSource> {
      TODO("Not yet implemented")
   }

   private fun createOpenApiSpec(service: Service, version: String = "1.0.0"): OpenAPI {

      val openApi = OpenAPI()
         .info(
            Info()
               .version(version)
               .title(service.qualifiedName.toQualifiedName().typeName)
         )
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

   private fun appendPathItem(
      operation: Operation,
      paths: Paths,
      components: Components,
   ) {
      val responseSchema = typeAsSchema(operation.returnType, components)

      val httpOperation = HttpOperation.fromAnnotation(operation.annotation(HttpOperation.NAME)!!)
      val oasOperation = io.swagger.v3.oas.models.Operation()
         .summary(operation.typeDoc)
         .responses(
            ApiResponses()
               .addApiResponse(
                  "200", ApiResponse()
                     .description(operation.typeDoc)
                     .content(
                        Content()
                           .addMediaType(
                              "application/json", MediaType().schema(
                                 responseSchema.wrapRefToObjectSchema()
                              )
                           )
                     )
               )
         )
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

   private fun buildRequestBody(parameters: List<lang.taxi.services.Parameter>, components: Components): RequestBody? {
      val requestBodyParam = parameters.firstOrNull { it.annotation("RequestBody") != null } ?: return null
      return RequestBody()
         .content(
            Content()
               .addMediaType(
                  "application/json",
                  MediaType().schema(typeAsSchema(requestBodyParam.type, components).wrapRefToObjectSchema())
               )
         )
   }

   private fun getParameterSource(parameter: lang.taxi.services.Parameter): String {
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

   fun generateOpenApiSpec(taxi: TaxiDocument): List<Pair<Service, OpenAPI>> {

      return taxi.services
         .filter { service -> service.operations.any { operation -> operation.annotation(HttpOperation.NAME) != null } }
         .map { service -> service to createOpenApiSpec(service) }

   }

   fun generateOpenApiSpecAsYaml(taxi: TaxiDocument): List<WritableSource> {
      return generateOpenApiSpec(taxi)
         .map { (service, openApi) ->
            val oasSpecName = "${service.toQualifiedName().typeName}.yaml"
            val yaml = Yaml31.pretty().writeValueAsString(openApi)
            SimpleWriteableSource(Path.of(oasSpecName), yaml)
         }
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
