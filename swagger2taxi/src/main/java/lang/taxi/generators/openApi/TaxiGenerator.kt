package lang.taxi.generators.openApi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.google.common.annotations.VisibleForTesting
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.openApi.swagger.SwaggerTaxiGenerator
import lang.taxi.generators.openApi.v3.OpenApiTaxiGenerator

data class GeneratorOptions(
   val serviceBasePath:String? = null
)

class TaxiGenerator(
   private val schemaWriter: SchemaWriter = SchemaWriter()
) {
   fun generateAsStrings(source: String, defaultNamespace: String, generatorOptions: GeneratorOptions = GeneratorOptions()): GeneratedTaxiCode {
      return when (detectVersion(source)) {
         SwaggerVersion.SWAGGER_2 -> SwaggerTaxiGenerator(schemaWriter).parseSwaggerV2(source, defaultNamespace)
         SwaggerVersion.OPEN_API -> OpenApiTaxiGenerator(schemaWriter).generateAsStrings(source, defaultNamespace, generatorOptions)
      }
   }

   @VisibleForTesting
   internal fun detectVersion(source: String): SwaggerVersion {
      return if (source.trim().startsWith("{")) {
         detectRightVersionFromSource(source, ObjectMapper())
      } else {
         detectRightVersionFromSource(source, YAMLMapper())
      }
   }

   private fun detectRightVersionFromSource(source: String, mapper: ObjectMapper): SwaggerVersion {
      val keys = mapper.readTree(source).fieldNames().asSequence().toList()
      return when {
         keys.contains("openapi") -> SwaggerVersion.OPEN_API
         keys.contains("swagger") -> SwaggerVersion.SWAGGER_2
         else -> error("Unable to determine the swagger version")
      }
   }

   enum class SwaggerVersion {
      SWAGGER_2,
      OPEN_API
   }


}
