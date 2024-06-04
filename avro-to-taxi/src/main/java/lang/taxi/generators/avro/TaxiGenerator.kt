package lang.taxi.generators.avro

import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import lang.taxi.generators.SourceMap
import org.apache.avro.Schema
import java.net.URI
import java.nio.file.Path

class TaxiGenerator(
   private val schemaWriter: SchemaWriter = SchemaWriter(),
   private val logger: Logger = Logger()
) {

   fun generate(schemaFile: Path): GeneratedTaxiCode {
      val avroSchema = Schema.Parser().parse(schemaFile.toFile())
      return generate(avroSchema, schemaFile.toAbsolutePath().toString())
   }

   fun generate(avroSchema: String, path: String): GeneratedTaxiCode {
      val schema = Schema.Parser().parse(avroSchema)
      return generate(schema, path)
   }

   /**
    * Generates a Taxi doc, and associated source code for the avro schema.
    * Note that the original avro schema is embedded in the compilation units of the
    * generated types. This is needed for serialization / deserialization
    */
   fun generate(avroSchema: Schema, uri: String): GeneratedTaxiCode {
      val generatedTypes = AvroTypeMapper(avroSchema, logger, uri).generateTypes()
      val taxiDoc = TaxiDocument(generatedTypes, emptySet())
      val taxi = schemaWriter.generateSchemas(listOf(taxiDoc))
      val sourceMap = SourceMap.forMembers(uri, generatedTypes)
      return GeneratedTaxiCode(taxi, logger.messages, sourceMap = sourceMap)
   }
}
