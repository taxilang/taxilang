package lang.taxi.generators.openApi.v3

import io.swagger.oas.models.OpenAPI
import io.swagger.oas.models.media.*
import lang.taxi.generators.openApi.Utils
import lang.taxi.generators.openApi.Utils.replaceIllegalCharacters
import lang.taxi.types.*

class OpenApiTypeMapper(private val api: OpenAPI, val defaultNamespace: String) {

   private val _generatedTypes = mutableMapOf<QualifiedName, Type>()

   val generatedTypes: Set<Type> get() = _generatedTypes.values.toSet()

   fun generateTypes() {
      api.components?.schemas?.forEach { (name, schema) ->
         generateNamedTypeRecursively(schema, qualify(name))
      }
   }

   private fun generateNamedTypeRecursively(
      schema: Schema<*>,
      name: QualifiedName
   ): Type = _generatedTypes.getOrPut(name) {
      if (schema.properties.isNullOrEmpty()) {
         val supertype = toType(schema, name.typeName)
         makeType(name, supertype)
      } else {
         makeModel(name, schema)
      }
   }

   fun generateUnnamedTypeRecursively(
      schema: Schema<*>,
      context: String
   ): Type =
      toType(schema, context) ?:
      schema.`$ref`?.getTypeFromRef() ?:
      when {
          schema is ComposedSchema && schema.allOf?.size == 1 -> {
             generateUnnamedTypeRecursively(schema.allOf.single(), context)
          }
          schema.hasProperties() -> {
             val name = anonymousModelName(context)
             _generatedTypes.getOrPut(name) {
                makeModel(name, schema)
             }
          }
          else -> null
      } ?:
      PrimitiveType.ANY

   private fun Schema<*>.hasProperties() = !properties.isNullOrEmpty()

   private fun toType(schema: Schema<*>, context: String) =
      primitiveTypeFor(schema) ?:
      intermediateTypeFor(schema) ?:
      if (schema is ArraySchema) {
         makeArrayType(schema, context + "Element")
      } else null

   private fun primitiveTypeFor(schema: Schema<*>) = when (schema) {
      is BooleanSchema -> PrimitiveType.BOOLEAN
      is DateSchema -> PrimitiveType.LOCAL_DATE
      is DateTimeSchema -> PrimitiveType.DATE_TIME
      is IntegerSchema -> PrimitiveType.INTEGER
      is NumberSchema -> PrimitiveType.DECIMAL
      is StringSchema -> PrimitiveType.STRING
      else -> null
   }

   private fun intermediateTypeFor(schema: Schema<*>): Type? = when (schema) {
      is BinarySchema,
      is FileSchema,
      is ByteArraySchema,
      is PasswordSchema,
      is UUIDSchema,
      is EmailSchema -> {
         val formatTypeQualifiedName = qualify(schema.format)
         _generatedTypes.getOrPut(formatTypeQualifiedName) {
            makeType(formatTypeQualifiedName, PrimitiveType.STRING)
         }
      }
      else -> null
   }

   private fun makeType(
      formatTypeQualifiedName: QualifiedName,
      supertype: Type? = null
   ) = ObjectType(
      formatTypeQualifiedName.toString(),
      ObjectTypeDefinition(
         inheritsFrom = setOfNotNull(supertype),
         compilationUnit = CompilationUnit.unspecified()
      )
   )

   private fun makeArrayType(schema: ArraySchema, context: String): ArrayType {
      val innerType = generateUnnamedTypeRecursively(schema.items, context)
      return ArrayType.of(innerType)
   }

   private fun makeModel(
      name: QualifiedName,
      schema: Schema<*>
   ): ObjectType {
      // This allows us to support recursion - the undefined type will prevent us getting into an endless loop
      _generatedTypes[name] = ObjectType.undefined(name.fullyQualifiedName)
      val requiredFields = schema.required ?: emptyList()
      val properties = schema.properties ?: emptyMap()
      val fields = properties.map { (propName, schema) ->
         generateField(
            name = propName,
            schema = schema,
            required = requiredFields.contains(propName),
            parent = name,
         )
      }
      val typeDef = ObjectTypeDefinition(
         fields = fields.toSet(),
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = schema.description,
      )
      return ObjectType(name.fullyQualifiedName, typeDef)
   }

   private fun generateField(name: String, schema: Schema<*>, required: Boolean, parent: QualifiedName): Field {
      val legalName = name.replaceIllegalCharacters()
      return Field(
         name = legalName,
         type = generateUnnamedTypeRecursively(schema, context = parent.typeName+legalName.capitalize()),
         nullable = required,
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = schema.description,
      )
   }

   private fun String.getTypeFromRef(): Type {
      val (name, schema) = RefEvaluator.navigate(this@OpenApiTypeMapper.api, this)
      return generateNamedTypeRecursively(schema, qualify(name))
   }

   private fun anonymousModelName(context: String): QualifiedName {
      val typeName =
         if (context.startsWith("AnonymousType")) context
         else "AnonymousType${context.capitalize()}"
      return qualify(typeName)
   }

   private fun qualify(name: String) =
      Utils.qualifyTypeNameIfRaw(name, defaultNamespace)
}
