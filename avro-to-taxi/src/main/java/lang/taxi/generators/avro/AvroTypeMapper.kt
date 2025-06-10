package lang.taxi.generators.avro

import lang.taxi.generators.DefaultTypeDeclaration
import lang.taxi.generators.FieldName
import lang.taxi.generators.Logger
import lang.taxi.generators.SchemaMetadataTaxiDefinition
import lang.taxi.generators.NamespacedType
import lang.taxi.generators.SchemaTypeDeclaration
import lang.taxi.generators.TypeDefinitionHelper
import lang.taxi.generators.avro.AvroTypeMapper.Companion.TAXI_TYPENAME
import lang.taxi.sources.SourceCode
import lang.taxi.sources.SourceCodeLanguages
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.EnumDefinition
import lang.taxi.types.EnumType
import lang.taxi.types.EnumValue
import lang.taxi.types.Field
import lang.taxi.types.MapType
import lang.taxi.types.Modifier
import lang.taxi.types.ObjectType
import lang.taxi.types.ObjectTypeDefinition
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.UnresolvedImportedType
import lang.taxi.utils.log
import org.apache.avro.Schema
import java.nio.file.Paths

class AvroTypeMapper(
   private val avroSchema: Schema,
   private val logger: Logger,
   private val path: String? = null
) {

   companion object {
      const val TAXI_TYPENAME = "taxi.dataType"

      val primitives = mapOf(
         Schema.Type.STRING to PrimitiveType.STRING,
         Schema.Type.INT to PrimitiveType.INTEGER,
         Schema.Type.BOOLEAN to PrimitiveType.BOOLEAN,
         Schema.Type.DOUBLE to PrimitiveType.DOUBLE,
         Schema.Type.FLOAT to PrimitiveType.DECIMAL,
         Schema.Type.FIXED to PrimitiveType.DECIMAL,
         Schema.Type.LONG to PrimitiveType.LONG
      )

   }

   private val _generatedTypes = mutableMapOf<QualifiedName, Type>()
   private val generatedTypes: Set<Type> get() = _generatedTypes.values.toSet()

   fun generateTypes(): Set<Type> {
      if (avroSchema.type == Schema.Type.ARRAY) {
         val elementType = avroSchema.elementType
         getOrCreateType(
            avroSchema.elementType,
            TypeDefinitionHelper.forHint(NamespacedType(elementType.namespace, elementType.name))
         )
      } else {
         getOrCreateType(
            avroSchema,
            TypeDefinitionHelper.forHint(NamespacedType(avroSchema.namespace, avroSchema.name))
         )
      }

      return generatedTypes
   }


   private fun getOrCreateType(schema: Schema, nameHelper: TypeDefinitionHelper): Type {
      return when (schema.type) {
         Schema.Type.ARRAY -> getArrayType(schema, nameHelper)
         Schema.Type.RECORD -> getRecordType(schema, nameHelper)
         Schema.Type.ENUM -> getEnumType(schema, nameHelper)
         Schema.Type.MAP -> getMapType(schema, nameHelper)
         else -> getOrCreateScalarType(schema, nameHelper)
      }
   }

   private fun getMapType(schema: Schema, nameHelper: TypeDefinitionHelper): Type {
      val valueType = getOrCreateType(
         schema.valueType, nameHelper
            .append(FieldName("MapEntryValue"))
            .appendNotNull(SchemaMetadataTaxiDefinition.ifPresent(schema.getProp(TAXI_TYPENAME)))
      )
      return MapType(PrimitiveType.STRING, valueType, CompilationUnit.unspecified())
   }

   private fun getEnumType(schema: Schema, nameHelper: TypeDefinitionHelper): Type {
      val name = nameHelper
         .appendNotNull(SchemaMetadataTaxiDefinition.ifPresent(schema.getProp(TAXI_TYPENAME)))
         .suggestName()

      return _generatedTypes.getOrPut(name) {
         val defaultValue = schema.enumDefault
         val symbols = schema.enumSymbols.mapIndexed { index, symbol ->
            val valueName = EnumValue.enumValueQualifiedName(name, symbol)
            EnumValue(name = symbol, value = index, valueName, isDefault = symbol == defaultValue)
         }
         EnumType(
            name.parameterizedName,
            EnumDefinition(
               symbols,
               typeDoc = schema.doc,
               compilationUnit = CompilationUnit.unspecified(),
               // This might need to be string?
               valueType = PrimitiveType.INTEGER
            )
         )
      }
   }

   private fun getOrCreateScalarType(schema: Schema, nameHelper: TypeDefinitionHelper): Type {
      val typeName = nameHelper.appendNotNull(SchemaMetadataTaxiDefinition.ifPresent(schema.getProp(TAXI_TYPENAME)))
         .suggestName()
      val declareNewType = nameHelper.declaresNewType(SchemaTypeDeclaration.DeclarationLocation.Field)
      if (!declareNewType) {
         return UnresolvedImportedType(typeName.parameterizedName)
      }


      return this._generatedTypes.getOrPut(typeName) {
         val baseType = getPrimitiveType(schema.type)
         ObjectType(
            typeName.parameterizedName,
            ObjectTypeDefinition(
               inheritsFrom = listOf(baseType),
               compilationUnit = CompilationUnit.unspecified()
            )
         )
      }


   }

   private fun getPrimitiveType(type: Schema.Type): PrimitiveType {
      return primitives.getOrElse(type) {
         error("type $type does not have a defined Primitive in Taxi")
      }
   }

   private fun getRecordType(schema: Schema, nameHelper: TypeDefinitionHelper): Type {
      val thisNameHelper =
         nameHelper.appendNotNull(SchemaMetadataTaxiDefinition.ifPresent(schema.getProp(TAXI_TYPENAME)))
      val typeName = thisNameHelper.suggestName()
      return _generatedTypes.getOrPut(typeName) {
         if (!thisNameHelper.declaresNewType(SchemaTypeDeclaration.DeclarationLocation.Model)) {
            return UnresolvedImportedType(typeName.parameterizedName)
         }


         // This allows us to support recursion - the undefined type will prevent us getting into an endless loop
         val undefined = ObjectType.undefined(typeName.fullyQualifiedName)
         _generatedTypes[typeName] = undefined

         val fields = schema.fields.mapIndexed { index, avroField: Schema.Field ->

            val taxiTypeDefinition = avroField.getTaxiMetadata(SchemaTypeDeclaration.DeclarationLocation.Field)
            val fieldNameHelper = thisNameHelper
               // Here's the logic (for legacy / backwards compatability, plus also usability):
               // If the user has not defined taxi metadata, then a type gets created, using default naming.
               // If the user HAS defined taxi metadata, (name only, not creation behaviour), then we use the default creation behaviour
               // If the user has defined taxi metadata (name + defineNewType), then use the logic they have declared.
               // So, as a fallback, set to declareNewType = true, which gets overridden if they have added other metadata
               .append(DefaultTypeDeclaration(declareNewType = true))
               .append(FieldName(avroField.name()))
               .appendNotNull(SchemaMetadataTaxiDefinition.ifPresent(taxiTypeDefinition))

            val (fieldSchema, nullable) = unwrapUnionType(avroField.schema())

            val typeRef = getOrCreateType(fieldSchema, fieldNameHelper)
            Field(
               name = avroField.name(),
               typeDoc = avroField.doc(),
               type = typeRef,
               nullable = nullable,
               annotations = listOf(AvroFieldAnnotation.annotation(index)),
               compilationUnit = CompilationUnit.unspecified()
            )
         }
         ObjectType(
            typeName.parameterizedName,
            ObjectTypeDefinition(
               fields = fields.toSet(),
               modifiers = listOf(Modifier.CLOSED),
               typeDoc = schema.doc,
               compilationUnit = avroCompilationUnit(schema, typeName, path),
               annotations = setOf(AvroMessageAnnotation.annotation())
            )
         )
      }

   }

   private fun unwrapUnionType(schema: Schema): Pair<Schema, IsNullable> {
      if (schema.type != Schema.Type.UNION) {
         return schema to false
      }
      val types = schema.types
      if (types.size == 1) {
         return types[0] to false
      }

      return if (types.size == 2 && types.any { it.type == Schema.Type.NULL }) {
         types.single { it.type != Schema.Type.NULL } to true
      } else {
         schema to false
      }
   }

   private fun getArrayType(schema: Schema, nameHelper: TypeDefinitionHelper): Type {
      val memberType = getOrCreateType(schema.elementType, nameHelper)
      return ArrayType(memberType, CompilationUnit.unspecified())
   }
}

private typealias IsNullable = Boolean

private fun avroCompilationUnit(schema: Schema, typeName: QualifiedName, fileName: String?): CompilationUnit {
   val sourceName = fileName ?: "Generated for ${typeName.parameterizedName}"
   val path = fileName?.let {
      try {
         Paths.get(it)
      } catch (e: Exception) {
         // Do nothing
         null
      }
   }
   return CompilationUnit(
      SourceCode(
         sourceName,
         schema.toString(true),
         path = path,
         language = SourceCodeLanguages.AVRO
      )
   )
}

fun Schema.Field.getTaxiMetadata(declarationLocation: SchemaTypeDeclaration.DeclarationLocation): SchemaTypeDeclaration? {
   this.getObjectProp(TAXI_TYPENAME)?.let { value ->
      when (value) {
         is Map<*, *> -> return SchemaTypeDeclaration.fromMap(value, declarationLocation)
         is String -> return SchemaTypeDeclaration.forName(value, declarationLocation)
         else -> {
            log().warn("Expected to find a Map<> when calling getObjectProp(), but got ${value::class.simpleName} - $value")
            return null
         }
      }
   }
   return this.getProp(TAXI_TYPENAME)?.let { typeName -> SchemaTypeDeclaration.forName(typeName, declarationLocation) }
}
