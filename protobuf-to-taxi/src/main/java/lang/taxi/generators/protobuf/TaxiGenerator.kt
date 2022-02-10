package lang.taxi.generators.protobuf

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaLoader
import lang.taxi.TaxiDocument
import lang.taxi.generators.GeneratedTaxiCode
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaWriter
import okio.FileSystem

// Note: The square filesystem used here should support zip files, which is handy!
class TaxiGenerator(
   private val fileSystem: FileSystem = FileSystem.SYSTEM,
   private val schemaWriter: SchemaWriter = SchemaWriter(),
   private val logger: Logger = Logger()
) {
   private val schemaLoader = SchemaLoader(fileSystem)
   private val rootPaths = mutableListOf<Location>()
   fun addSchemaRoot(path: String): TaxiGenerator {
      rootPaths.add(Location.get(path))
      return this
   }

   val protobufSchema: Schema by lazy {
      schemaLoader.initRoots(rootPaths)
      schemaLoader.loadSchema()
   }

   fun generate(
      packagesToInclude: List<String> = listOf("*"),
      // Allows for easier testing.  Almost never change this in prod
      protobufSchema: Schema = this.protobufSchema
   ): GeneratedTaxiCode {
      val generatedTypes = ProtobufTypeMapper(protobufSchema, logger).generateTypes(packagesToInclude)
      val taxiDoc = TaxiDocument(generatedTypes, emptySet())
      val taxi = schemaWriter.generateSchemas(listOf(taxiDoc))
      return GeneratedTaxiCode(taxi, logger.messages)
   }


}
