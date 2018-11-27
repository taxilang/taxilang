package lang.taxi.generators.openApi

import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import v2.io.swagger.models.Swagger
import v2.io.swagger.parser.SwaggerParser

class TaxiGenerator(
        private val schemaWriter: SchemaWriter = SchemaWriter()
) {
    fun generateAsStrings(source: String, defaultNamespace: String): GeneratedTaxiCode {
        val swagger: Swagger = SwaggerParser().parse(source)
        val logger = Logger()
        val typeGenerator = SwaggerTypeMapper(swagger, defaultNamespace, logger)
        val serviceGenerator = SwaggerServiceGenerator(swagger, typeGenerator, logger)

        val types = typeGenerator.generateTypes()
        val services = serviceGenerator.generateServices()

        val taxi = schemaWriter.generateSchemas(
                listOf(TaxiDocument(types, services))
        )
        return GeneratedTaxiCode(taxi, logger.messages)
    }
}