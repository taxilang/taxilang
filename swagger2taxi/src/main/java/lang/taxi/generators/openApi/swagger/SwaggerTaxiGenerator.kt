package lang.taxi.generators.openApi.swagger

import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.openApi.SwaggerServiceGenerator
import v2.io.swagger.models.Swagger
import v2.io.swagger.parser.SwaggerParser

class SwaggerTaxiGenerator(private val schemaWriter: SchemaWriter) {
   fun parseSwaggerV2(source: String, defaultNamespace: String): GeneratedTaxiCode {
      val logger = Logger()
      val swagger: Swagger = SwaggerParser().parse(source)
         ?: return logger.failure("Failed to parse the swagger file provided.  This is either an issue with the swagger file, or the swagger parser itself, not an issue with this plugin.  Check that the swagger spec is valid, and try again.")

      val typeGenerator = SwaggerTypeMapper(swagger, defaultNamespace, logger)
      val serviceGenerator = SwaggerServiceGenerator(swagger, typeGenerator, logger)

      val services = serviceGenerator.generateServices()
      val types = typeGenerator.generateTypes()

      val taxi = schemaWriter.generateSchemas(
         listOf(TaxiDocument(types, services, emptySet()))
      ).filter { generated ->
         // Filter empty junk
         // We sometimes get empty files with just a namespace, and no content.
         generated.contains("type") || generated.contains("model") || generated.contains("service")
      }


      return GeneratedTaxiCode(taxi, logger.messages)
   }

   fun parseVersion(source: String): String? {
      val swagger: Swagger = SwaggerParser().parse(source)
      return swagger.info.version
   }
}
