package lang.taxi.generators.jsonSchema

import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URL

class TaxiGenerator(
   private val schemaWriter: SchemaWriter = SchemaWriter(),
   private val schemaLoader: SchemaLoader.SchemaLoaderBuilder = SchemaLoader.builder(),
   private val logger: Logger = Logger()
) {
   fun generateAsStrings(source: String, defaultNamespace: String? = null): GeneratedTaxiCode {
      val jsonObject = JSONObject(JSONTokener(source))
      return generateFromJsonObject(jsonObject, defaultNamespace)
   }

   fun generateAsStrings(source: URL, defaultNamespace: String? = null): GeneratedTaxiCode {
      val schemaJson = source.toJsonObject()
      return generateFromJsonObject(schemaJson, defaultNamespace)
   }

   private fun generateFromJsonObject(
      schemaJson: JSONObject,
      defaultNamespace: String?
   ): GeneratedTaxiCode {
      val loader = schemaLoader.schemaJson(schemaJson)
         .build()
      val schema = loader.load().build()
      return generateFromSchema(schema, defaultNamespace)
   }

   fun generateFromSchema(jsonSchema: Schema, defaultNamespace: String? = null): GeneratedTaxiCode {
      val generatedTypes = JsonSchemaTypeMapper(
         jsonSchema,
         logger,
         defaultNamespace ?: JsonSchemaTypeMapper.getDefaultNamespace(jsonSchema, logger)
      ).generateTypes()
      val taxiDoc = TaxiDocument(generatedTypes, emptySet())
      val taxi = schemaWriter.generateSchemas(listOf(taxiDoc))
      return GeneratedTaxiCode(taxi, emptyList())
   }

}

fun URL.toJsonObject(): JSONObject {
   return JSONObject(JSONTokener(this.openStream()))
}
