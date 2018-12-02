package lang.taxi.generators.openApi

import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.openApi.swagger.SwaggerTaxiGenerator
import lang.taxi.generators.openApi.v3.OpenApiTaxiGenerator

class TaxiGenerator(
        private val schemaWriter: SchemaWriter = SchemaWriter()
) {
    fun generateAsStrings(source: String, defaultNamespace: String): GeneratedTaxiCode {
        return if (source.trim().startsWith("openapi")) {
            OpenApiTaxiGenerator(schemaWriter).generateAsStrings(source, defaultNamespace)
        } else {
            SwaggerTaxiGenerator(schemaWriter).parseSwaggerV2(source, defaultNamespace)
        }

    }


}