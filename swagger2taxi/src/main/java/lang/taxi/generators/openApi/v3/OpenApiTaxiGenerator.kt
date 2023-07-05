package lang.taxi.generators.openApi.v3

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
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

      val parseOptions = ParseOptions()
      parseOptions.isResolve = true
      val spec = OpenAPIV3Parser().readContents(source, emptyList(), parseOptions)

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
      val openApi = Yaml.mapper().readValue(source, OpenAPI::class.java)
      return openApi.info.version
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
