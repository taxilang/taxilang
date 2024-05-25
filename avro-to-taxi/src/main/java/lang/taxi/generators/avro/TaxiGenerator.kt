package lang.taxi.generators.avro

import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import org.apache.avro.Schema
import java.nio.file.Path

class TaxiGenerator(
   private val schemaWriter: SchemaWriter = SchemaWriter(),
   private val logger: Logger = Logger()
) {

   fun generate(schemaFile: Path):GeneratedTaxiCode {
      val avroSchema = Schema.Parser().parse(schemaFile.toFile())
      return generate(avroSchema)
   }
   fun generate(avroSchema:String):GeneratedTaxiCode {
      val schema = Schema.Parser().parse(avroSchema)
      return generate(schema)
   }
   fun generate(avroSchema: Schema): GeneratedTaxiCode {
      val generatedTypes = AvroTypeMapper(avroSchema, logger).generateTypes()
      val taxiDoc = TaxiDocument(generatedTypes, emptySet())
      val taxi = schemaWriter.generateSchemas(listOf(taxiDoc))
      return GeneratedTaxiCode(taxi, logger.messages)
   }
}
