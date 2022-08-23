package lang.taxi.generators.openApi.v3

import io.swagger.oas.models.OpenAPI
import io.swagger.parser.models.ParseOptions
import io.swagger.parser.v3.OpenAPIV3Parser
import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.openApi.GeneratorOptions
import lang.taxi.services.Service
import lang.taxi.types.Type

class OpenApiTaxiGenerator(private val schemaWriter: SchemaWriter) {
   fun generateAsStrings(
      source: String,
      defaultNamespace: String,
      options: GeneratorOptions = GeneratorOptions()
   ): GeneratedTaxiCode {
      val logger = Logger()
      val spec = OpenAPIV3Parser().readContents(source, emptyList(), ParseOptions())

      val (services, types) = generateTaxiObjects(
         spec.openAPI,
         defaultNamespace,
         logger,
         options,
      )
      val taxi = schemaWriter.generateSchemas(
         listOf(TaxiDocument(types, services, emptySet()))
      )
      return GeneratedTaxiCode(taxi, logger.messages)
   }

   fun parseVersion(source: String): String? {
      val spec = OpenAPIV3Parser().readContents(source, emptyList(), ParseOptions())
      return spec.openAPI.info.version
   }

   companion object {
      fun generateTaxiObjects(
         openAPI: OpenAPI,
         defaultNamespace: String,
         logger: Logger = Logger(),
         options: GeneratorOptions = GeneratorOptions(),
      ): Pair<Set<Service>, Set<Type>> {
         val typeGenerator = OpenApiTypeMapper(openAPI, defaultNamespace)
         val serviceGenerator =
            OpenApiServiceMapper(openAPI, typeGenerator, logger)
         typeGenerator.generateTypes()
         val services = serviceGenerator.generateServices(options.serviceBasePath)
         val types = typeGenerator.generatedTypes
         return Pair(services, types)
      }
   }
}
