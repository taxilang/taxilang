package lang.taxi.generators.avro

import lang.taxi.generators.FieldName
import lang.taxi.generators.Logger
import lang.taxi.generators.NameFromTaxiMetadata
import lang.taxi.generators.NamespacedType
import lang.taxi.generators.TypeNameHelper
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
import org.apache.avro.Schema

class AvroTypeMapper(private val avroSchema: Schema, private val logger: Logger) {

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
         getOrCreateType(avroSchema.elementType, TypeNameHelper.forHint(NamespacedType(elementType.namespace, elementType.name)))
      } else {
         getOrCreateType(avroSchema, TypeNameHelper.forHint(NamespacedType(avroSchema.namespace, avroSchema.name)))
      }

      return generatedTypes
   }


   private fun getOrCreateType(schema: Schema, nameHelper: TypeNameHelper): Type {
      return when (schema.type) {
         Schema.Type.ARRAY -> getArrayType(schema, nameHelper)
         Schema.Type.RECORD -> getRecordType(schema, nameHelper)
         Schema.Type.ENUM -> getEnumType(schema, nameHelper)
         Schema.Type.MAP -> getMapType(schema, nameHelper)
         else -> getOrCreateScalarType(schema, nameHelper)
      }
   }

   private fun getMapType(schema: Schema, nameHelper: TypeNameHelper): Type {
      val valueType = getOrCreateType(
         schema.valueType, nameHelper
            .append(FieldName("MapEntryValue"))
            .appendNotNull(NameFromTaxiMetadata.ifPresent(schema.getProp(TAXI_TYPENAME)))
      )
      return MapType(PrimitiveType.STRING, valueType, CompilationUnit.unspecified())
   }

   private fun getEnumType(schema: Schema, nameHelper: TypeNameHelper): Type {
      val name = nameHelper
         .appendNotNull(NameFromTaxiMetadata.ifPresent(schema.getProp(TAXI_TYPENAME)))
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
               basePrimitive = PrimitiveType.INTEGER
            )
         )
      }
   }

   private fun getOrCreateScalarType(schema: Schema, nameHelper: TypeNameHelper): Type {
      val typeName = nameHelper.appendNotNull(NameFromTaxiMetadata.ifPresent(schema.getProp(TAXI_TYPENAME)))
         .suggestName()

      return this._generatedTypes.getOrPut(typeName) {
         val baseType = getPrimitiveType(schema.type)
         ObjectType(
            typeName.parameterizedName,
            ObjectTypeDefinition(
               inheritsFrom = setOf(baseType),
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

   private fun getRecordType(schema: Schema, nameHelper: TypeNameHelper): Type {
      val thisNameHelper = nameHelper.appendNotNull(NameFromTaxiMetadata.ifPresent(schema.getProp(TAXI_TYPENAME)))
      val typeName = thisNameHelper.suggestName()
      return _generatedTypes.getOrPut(typeName) {
         // This allows us to support recursion - the undefined type will prevent us getting into an endless loop
         val undefined = ObjectType.undefined(typeName.fullyQualifiedName)
         _generatedTypes[typeName] = undefined

         val fields = schema.fields.mapIndexed { index, avroField ->
            val fieldNameHelper = thisNameHelper.append(FieldName(avroField.name()))
               .appendNotNull(NameFromTaxiMetadata.ifPresent(avroField.getProp(TAXI_TYPENAME)))


            val (fieldSchema, nullable) = unwrapPotentialNullableType(avroField.schema())

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
               compilationUnit = CompilationUnit.unspecified(),
               annotations = setOf(AvroMessageAnnotation.annotation())
            )
         )
      }

   }

   private fun unwrapPotentialNullableType(schema: Schema): Pair<Schema, Boolean> {
      if (schema.type != Schema.Type.UNION) {
         return schema to false
      }
      val types = schema.types
      return if (types.size == 2 && types.any { it.type == Schema.Type.NULL }) {
         types.single { it.type != Schema.Type.NULL } to true
      } else {
         schema to false
      }
   }

   private fun getArrayType(schema: Schema, nameHelper: TypeNameHelper): Type {
      val memberType = getOrCreateType(schema.elementType, nameHelper)
      return ArrayType(memberType, CompilationUnit.unspecified())
   }
}
