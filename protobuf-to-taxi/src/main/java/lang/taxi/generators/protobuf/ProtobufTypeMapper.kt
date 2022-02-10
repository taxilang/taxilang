package lang.taxi.generators.protobuf

import com.squareup.wire.schema.EnumType
import com.squareup.wire.schema.MessageType
import com.squareup.wire.schema.ProtoType
import com.squareup.wire.schema.Schema
import lang.taxi.generators.Logger
import lang.taxi.types.Annotation
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.EnumDefinition
import lang.taxi.types.EnumValue
import lang.taxi.types.Field
import lang.taxi.types.ObjectType
import lang.taxi.types.ObjectTypeDefinition
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeKind
import lang.taxi.types.toQualifiedName

class ProtobufTypeMapper(
   private val protoSchema: Schema,
   private val logger: Logger
) {
   private val _generatedTypes = mutableMapOf<QualifiedName, Type>()
   private val generatedTypes: Set<Type> get() = _generatedTypes.values.toSet()

   init {
      createBuiltInTypes()
   }

   private fun createBuiltInTypes() {
      mapOf(
         ProtoType.ANY to PrimitiveType.ANY,
         ProtoType.BOOL to PrimitiveType.BOOLEAN,
         ProtoType.BYTES to PrimitiveType.ANY, // TODO
         ProtoType.DOUBLE to PrimitiveType.DOUBLE,
         ProtoType.DURATION to PrimitiveType.ANY, // TODO
         ProtoType.FIXED32 to PrimitiveType.INTEGER,
         ProtoType.FIXED64 to PrimitiveType.DECIMAL,
         ProtoType.FLOAT to PrimitiveType.DECIMAL,
         ProtoType.INT32 to PrimitiveType.INTEGER,
         ProtoType.INT64 to PrimitiveType.DECIMAL,
         ProtoType.SFIXED32 to PrimitiveType.INTEGER,
         ProtoType.SFIXED64 to PrimitiveType.DECIMAL,
         ProtoType.SINT32 to PrimitiveType.INTEGER,
         ProtoType.SINT64 to PrimitiveType.DECIMAL,
         ProtoType.STRING to PrimitiveType.STRING,
         ProtoType.TIMESTAMP to PrimitiveType.INSTANT,
         ProtoType.UINT32 to PrimitiveType.INTEGER,
         ProtoType.UINT64 to PrimitiveType.DECIMAL,
      ).forEach { (protoType, taxiType) ->
         _generatedTypes[protoType.toString().toQualifiedName()] = taxiType
      }
   }


   fun generateTypes(packagesToInclude: List<String> = listOf("*")): Set<Type> {
      protoSchema.types
         .filter { protoType ->
            val qualifiedName = QualifiedName.from(protoType.toString())
            if (packagesToInclude.contains("*")) {
               !qualifiedName.namespace.startsWith("google.protobuf") // exclude internal protobuf types
            } else {
               packagesToInclude.any { it.startsWith(qualifiedName.namespace) }
            }
         }
         .map { protoType ->
            getOrCreateType(QualifiedName.from(protoType.toString()))
         }
      return generatedTypes
   }

   private fun getOrCreateType(type: ProtoType): Type {
      return getOrCreateType(QualifiedName.from(type.toString()))
   }

   private fun getOrCreateType(name: QualifiedName): Type {
      return _generatedTypes.getOrPut(name) {
         // This allows us to support recursion - the undefined type will prevent us getting into an endless loop
         val undefined = ObjectType.undefined(name.fullyQualifiedName)
         _generatedTypes[name] = undefined
         val protobufType = protoSchema.getType(name.parameterizedName)
         if (protobufType == null) {
            logger.error("Type ${name.parameterizedName} was not found in the protobuf schema - will use a stub type to continue, but this is a critical error")
            undefined
         } else {
            createType(protobufType)
         }
      }
   }

   private fun createType(
      type: com.squareup.wire.schema.Type
   ): Type {
      val generated = when (type) {
         is MessageType -> createModel(type)
         is EnumType -> createEnum(type)
         else -> TODO("Add support for proto type ${type::class.simpleName}")
      }
      return generated
   }

   private fun createEnum(type: EnumType): Type {
      val enumName = type.type.toString()
      val enumValues = type.constants.map { enumValue ->
         EnumValue(
            enumValue.name,
            enumValue.tag,
            EnumValue.enumValueQualifiedName(enumName.toQualifiedName(), enumValue.name),
            typeDoc = enumValue.documentation
         )
      }

      return lang.taxi.types.EnumType(
         enumName,
         EnumDefinition(
            enumValues,
            annotations = listOf(
               Annotation(ProtobufMessageAnnotation.NAME)
            ),
            typeDoc = type.documentation,
            basePrimitive = PrimitiveType.STRING,
            compilationUnit = CompilationUnit.unspecified()
         )
      )
   }

   private fun createModel(type: MessageType): Type {
      val fields = type.fields.map { protoField ->
         val fieldTypeOrCollectionMemberType = protoField.type?.let { getOrCreateType(it) } ?: run {
            logger.error("Field ${protoField.name} on $type did not expose a type.  This suggests a problem with loading the schema")
            PrimitiveType.ANY
         }
         val fieldType = if (protoField.isRepeated) {
            ArrayType.of(fieldTypeOrCollectionMemberType)
         } else fieldTypeOrCollectionMemberType
         val protoTag = protoField.tag
         Field(
            protoField.name,
            fieldType,
            !protoField.isRequired,
            typeDoc = protoField.documentation,
            defaultValue = protoField.default,
            compilationUnit = CompilationUnit.unspecified(),
            annotations = listOf(
               ProtobufFieldAnnotation.annotation(
                  protoField.tag,
                  protoField.type?.toString() ?: "error: No type specified"
               )
            )

         )
      }
      return ObjectType(
         type.type.toString(),
         ObjectTypeDefinition(
            fields.toSet(),
            annotations = setOf(
               ProtobufMessageAnnotation.annotation(type.type.enclosingTypeOrPackage,
               type.type.simpleName)
            ),
            typeKind = TypeKind.Model,
            typeDoc = type.documentation,
            compilationUnit = CompilationUnit.unspecified()
         )
      )
   }

}

