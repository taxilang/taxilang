package lang.taxi.generators.openApi.v3

import io.swagger.parser.models.ParseOptions
import io.swagger.parser.v3.OpenAPIV3Parser
import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter

class OpenApiTaxiGenerator(private val schemaWriter: SchemaWriter) {
    fun generateAsStrings(source: String, defaultNamespace: String): GeneratedTaxiCode {
        val logger = Logger()
        val spec = OpenAPIV3Parser().readContents(source, emptyList(), ParseOptions())

        val typeGenerator = OpenApiTypeMapper(spec.openAPI, defaultNamespace, logger)
        val serviceGenerator = OpenApiServiceMapper(spec.openAPI, typeGenerator, logger)
        val types = typeGenerator.generateTypes()
        val services = serviceGenerator.generateServices()
        val taxi = schemaWriter.generateSchemas(
                listOf(TaxiDocument(types, services, emptySet()))
        )
        return GeneratedTaxiCode(taxi, logger.messages)
    }
}