package lang.taxi.generators.openApi

import lang.taxi.TaxiDocument
import lang.taxi.generators.SchemaWriter
import v2.io.swagger.models.Swagger
import v2.io.swagger.parser.SwaggerParser

class TaxiGenerator(
        private val schemaWriter: SchemaWriter = SchemaWriter()
) {
    fun generateAsStrings(source: String, defaultNamespace: String): List<String> {
        val swagger: Swagger = SwaggerParser().parse(source)
        val typeGenerator = SwaggerTypeMapper(swagger, defaultNamespace)
        val serviceGenerator = SwaggerServiceGenerator(swagger, typeGenerator)

        val types = typeGenerator.generateTypes()
        val services = serviceGenerator.generateServices()

        return schemaWriter.generateSchemas(
                listOf(TaxiDocument(types, services))
        )
    }

}