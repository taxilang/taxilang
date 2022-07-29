package lang.taxi.generators.jsonSchema

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.generators.Logger
import lang.taxi.generators.NamingUtils.replaceIllegalCharacters
import lang.taxi.generators.NamingUtils.toCapitalizedWords
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.EnumDefinition
import lang.taxi.types.EnumType
import lang.taxi.types.EnumValue
import lang.taxi.types.Field
import lang.taxi.types.Model
import lang.taxi.types.ObjectType
import lang.taxi.types.ObjectTypeDefinition
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypeKind
import lang.taxi.types.UserType
import lang.taxi.utils.takeTail
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.EmptySchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.FalseSchema
import org.everit.json.schema.FormatValidator
import org.everit.json.schema.NullSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.ReferenceSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.SchemaLocation
import org.everit.json.schema.StringSchema
import java.net.URI


class JsonSchemaTypeMapper(
   private val jsonSchema: Schema,
   val logger: Logger = Logger(JsonSchemaTypeMapper::class.java),
   val defaultNamespace: String = getDefaultNamespace(
      jsonSchema,
      logger
   ),

   ) {

   data class GeneratedType(val source: Schema, val type: Type)

   private val _generatedTypes = mutableMapOf<QualifiedName, GeneratedType>()

   val generatedTypes: Set<Type> get() = _generatedTypes.values.map { it.type }.toSet()

   companion object {
      fun getSchemaId(schema: Schema, logger: Logger): Either<String, SchemaLocation> {
         return when {
            schema.id != null -> schema.id.left()
            // These are old id properties (v4 of the spec)
            schema.unprocessedProperties.containsKey("id") -> schema.unprocessedProperties["id"]!!.toString().left()
            schema.location != null -> {
               schema.location.right()
            }
            else -> error("Could not parse a schema id for schema at location ${schema.schemaLocation}")
         }
      }

      fun getSchemaIdAsUri(schema: Schema, logger: Logger): URI? {
         return try {
            when (val schemaId = getSchemaId(schema, logger)) {
               is Either.Left -> URI.create(schemaId.a)
               is Either.Right -> URI.create(schemaId.b.toString())
            }

         } catch (e: Exception) {
            logger.warn("Could not parse namespace from uri ${schema.schemaLocation} - ${e::class.simpleName} - ${e.message}")
            null
         }
      }

      fun getDefaultNamespace(schema: Schema, logger: Logger): String {
         val uri = getSchemaIdAsUri(schema, logger) ?: error("Could not parse schema to URI")
         return getNamespace(uri, logger)
      }

      fun getNamespace(uri: URI, logger: Logger): String {
         val namespace = uri.host.split(".")
            .reversed()
            .removeEmpties()
            .joinToString(".")
         return namespace
      }

      fun getTypeName(uri: URI, logger: Logger): QualifiedName {
         val defaultNamespace = getNamespace(uri, logger)
         val path = uri.path.removePrefix("/").removeSuffix(".json").removeSuffix(".schema").removeSuffix("/")
         val nameFromPath = path.split("/").last().toCapitalizedWords()
         val (additionalNamespaceElements: List<String>, typeName: String) = if (!uri.fragment.isNullOrEmpty()) {
            val fragmentParts = uri.fragment.split("/")
               .removeEmpties()
            val (typeName, remainingFragments) = fragmentParts.takeTail()
            (listOf(nameFromPath) + remainingFragments).removeEmpties() to typeName.capitalize()
         } else {
            emptyList<String>() to nameFromPath
         }

         val namespace = (listOf(defaultNamespace) + additionalNamespaceElements.map { it.decapitalize() })
            .joinToString(".")
         return QualifiedName(namespace, typeName)
      }
   }

   fun generateTypes(): Set<Type> {
      this.getTypeFromSchema(jsonSchema)
      return generatedTypes
   }

   /**
    * Entry point for top-level schema objects.
    * Can problably be simplified away
    */
   private fun getTypeFromSchema(schema: Schema, fallbackTypeName: QualifiedName? = null): Type {
      val resolvedSchema = schema.resolve()
      val typeName = getTypeNameFromSchema(resolvedSchema, fallback = fallbackTypeName)
      return getOrCreateType(typeName, resolvedSchema)
   }

   private fun getOrCreateType(name: QualifiedName, schema: Schema): Type {
      val resolvedSchema = schema.resolve()
      if (resolvedSchema is ArraySchema) {
         // createType will not create / cache an array type (for good reason, or all
         // future requests for type Foo will result in a Foo[]
         // So, if this was asked for as an array, wrap it into an ArrayType
         val type = getOrCreateType(name, resolvedSchema.allItemSchema)
         return ArrayType.of(type)
      }


      val generatedType = _generatedTypes.getOrPut(name) {
         // This allows us to support recursion - the undefined type will prevent us getting into an endless loop
         _generatedTypes[name] = GeneratedType(resolvedSchema, ObjectType.undefined(name.fullyQualifiedName))
         createType(resolvedSchema, name)
      }
      val generatedSourceSchema = generatedType.source
      if (!schema.resolvesEqualTo(generatedSourceSchema)) {
         fun logTypeNeedsInvestigationWarning(typeName: QualifiedName, cause: String) {
            logger.warn("Type needs investigation: $typeName - Cause: $cause")
         }

         logTypeNeedsInvestigationWarning(
            name,
            "Was found resolving to multiple JsonSchema definitions.  All usages should be checked"
         )

         // Were any types generated from this schema?
         val generatedFromThisSource = _generatedTypes.values.singleOrNull { it.source == resolvedSchema }
         if (generatedFromThisSource != null) {
            logTypeNeedsInvestigationWarning(
               generatedFromThisSource.type.toQualifiedName(),
               "This type was also resolved to name $name.  All usages of both types should be checked"
            )
            return generatedFromThisSource.type
         }

         val newName = findNextUnusedQualifiedName(name)
         logTypeNeedsInvestigationWarning(
            newName,
            "Was originally requested with name $name but a conflicting schema definition was detected"
         )
         return getOrCreateType(newName, schema)
      }

      return generatedType.type
   }

   private fun findNextUnusedQualifiedName(name: QualifiedName): QualifiedName {
      val originalName = name.fullyQualifiedName.substringBefore("\$Duplicate")
      var suffixToAttempt = 0;
      var proposedName = "";
      do {
         suffixToAttempt++
         proposedName = "$originalName\$Duplicate$suffixToAttempt"
      } while (_generatedTypes.containsKey(QualifiedName.from(proposedName)))

      return QualifiedName.from(proposedName)
   }

   private fun replaceType(name: QualifiedName, schema: Schema): Type {
      _generatedTypes.remove(name)
      return getOrCreateType(name, schema)
   }

   private fun createType(
      schema: Schema,
      name: QualifiedName
   ): GeneratedType {
      val createdType = when (schema) {
         is StringSchema -> {
            val scalarType = selectScalarTypeFromFormattedStringType(schema.formatValidator)
            generateScalarType(name, scalarType, schema.description)

         }
         is BooleanSchema -> generateScalarType(name, PrimitiveType.BOOLEAN, schema.description)
         is NumberSchema -> generateScalarType(name, getNumberType(schema), schema.description)
         is ObjectSchema -> generateModel(name, schema)
         is ArraySchema -> generateArrayMemberType(name, schema)
         is ReferenceSchema -> getTypeFromReferenceSchema(name, schema)
         is CombinedSchema -> generateCombinedSchema(name, schema)
         is EnumSchema -> generateEnum(name, schema)
         else -> TODO("Add support for schema type ${schema::class.simpleName}")
      }
      return GeneratedType(schema, createdType)
   }

   private fun selectScalarTypeFromFormattedStringType(formatValidator: FormatValidator?): PrimitiveType {
      if (formatValidator == null) {
         return PrimitiveType.STRING
      }
      return when (formatValidator.formatName()) {
         "date" -> PrimitiveType.LOCAL_DATE
         "date-time" -> PrimitiveType.INSTANT
         "time" -> PrimitiveType.TIME
         "unnamed-format" -> PrimitiveType.STRING
         else -> {
            logger.warn("Format of type ${formatValidator.formatName()} is not supported - defaulting to String")
            PrimitiveType.STRING
         }
      }
   }

   private fun generateEnum(name: QualifiedName, schema: EnumSchema): Type {
      val enumValues = schema.possibleValuesAsList.map {
         EnumValue(
            it.toString(),
            qualifiedName = EnumValue.enumValueQualifiedName(name, it.toString())
         )
      }
      return EnumType(
         name.fullyQualifiedName,
         EnumDefinition(
            enumValues,
            compilationUnit = CompilationUnit.unspecified(),
            typeDoc = schema.description,
            basePrimitive = PrimitiveType.STRING
         )
      )
   }

   private fun generateCombinedSchema(name: QualifiedName, schema: CombinedSchema): Type {
      // Edge case - we see some schemas wrap EnumType with a base type - in which case,
      // just generate it as an enum type
      if (isEnumTypeWrappedInCombinedSchema(schema)) {
         val enumSchema = schema.subschemas.first { it is EnumSchema }
         return replaceType(name, enumSchema)
      }

      val types = schema.subschemas.mapIndexed { index, subSchema ->
         val fallbackTypeName = QualifiedName(
            name.namespace,
            name.typeName + "_" + subSchema::class.simpleName!!.removeSuffix("Schema") + index
         )
         getTypeFromSchema(subSchema, fallbackTypeName)
      }
      // We don't support union types at present
      val unionPlaceholder = ObjectType(
         name.fullyQualifiedName,
         ObjectTypeDefinition(
            inheritsFrom = setOf(PrimitiveType.ANY),
            typeDoc = "Union types are not currently supported.  Could be any of:\n${types.joinToString(separator = "\n") { " * " + it.toQualifiedName().parameterizedName }}",
            compilationUnit = CompilationUnit.unspecified()
         )
      )
      return unionPlaceholder
   }

   private fun isEnumTypeWrappedInCombinedSchema(schema: CombinedSchema): Boolean {
      return schema.subschemas.size == 2 && schema.subschemas.count { it is EnumSchema } == 1 && schema.subschemas.count { it is StringSchema } == 1
   }

   private fun getTypeFromReferenceSchema(name: QualifiedName, schema: ReferenceSchema): Type {
      val type = getOrCreateType(name, schema.referredSchema)
      // We may have ended up here after seeing a reference type for the first time.#
      // In that case, we have an undefined placeholder type registered against this type
      // Now that we have the actual schema, replace the type
      return if (type is UserType<*, *> && !type.isDefined) {
         replaceType(name, schema.referredSchema)
      } else {
         type
      }

   }

   /**
    * Just generates a type that the array will contain.
    * Does not return an ArrayType - it's left to the caller to do this mapping.
    *
    * Otherwise, when we get a request for an array of type Foo, then
    * the request is registered with an Foo[],
    * meaning next time we ask for just a Foo, we incorrectly get an Foo[].
    *
    * See also: getOrCreateType() where we then wrap the type back into the array, after the
    * inner type has been cached.
    */
   private fun generateArrayMemberType(name: QualifiedName, schema: ArraySchema): Type {
      return getOrCreateType(name, schema.allItemSchema)
   }

   private fun generateModel(name: QualifiedName, schema: ObjectSchema): Model {
      var doc = """${schema.description?.orEmpty()}
         |
         |This model has been generated.  The original source is shown below.
         |
         |```json
         |${schema.toString()}
         |```
      """.trimMargin().trim()

      val fields = schema.propertySchemas.map { (propertyName, propertySchema) ->
         val qualifiedName = getTypeNameFromSchema(propertySchema, propertyName)
         val type = getOrCreateType(qualifiedName, propertySchema)
         val isRequired = schema.requiredProperties.contains(propertyName)
         val nullable = !isRequired
         Field(
            propertyName,
            type,
            nullable,
            defaultValue = propertySchema.defaultValue,
            compilationUnit = CompilationUnit.unspecified()
         )
      }

      return if (fields.isEmpty()) {
         val warning =
            "Type $name is defined as an object, but defined no attributes.  Will type this as an Any, however there may be challenges in reading this type.  Consider improving the source schema"
         logger.warn(warning)
         doc += "\n$warning"

         ObjectType(
            name.fullyQualifiedName,
            ObjectTypeDefinition(
               fields = fields.toSet(),
               typeKind = TypeKind.Type,
               inheritsFrom = setOf(PrimitiveType.ANY),
               compilationUnit = CompilationUnit.unspecified(),
               typeDoc = doc
            )
         )
      } else {
         ObjectType(
            name.fullyQualifiedName,
            ObjectTypeDefinition(
               fields = fields.toSet(),
               typeKind = TypeKind.Model,
               compilationUnit = CompilationUnit.unspecified(),
               typeDoc = doc
            )
         )
      }
   }

   private fun getNumberType(schema: NumberSchema): Type {
      return when {
         schema.requiresInteger() -> PrimitiveType.INTEGER
         // In JsonSchema things are either integer or not - and given "not" needs to include decimals, this feels like a decent choice
         else -> PrimitiveType.DECIMAL

      }
   }

   private fun generateScalarType(name: QualifiedName, baseType: Type, description: String?): Type {
      return ObjectType(
         name.fullyQualifiedName,
         ObjectTypeDefinition(
            inheritsFrom = setOf(baseType),
            compilationUnit = CompilationUnit.unspecified(),
            typeDoc = description
         )
      )
   }

   private fun getTypeNameFromSchema(
      schema: Schema,
      propertyName: String? = null,
      fallback: QualifiedName? = null
   ): QualifiedName {
      if (propertyName != null) return QualifiedName(defaultNamespace, propertyName.capitalize())
      // We can fall through here with simple types from CombinedSchema
      when (schema) {
         is StringSchema -> return PrimitiveType.STRING.toQualifiedName()
         is NumberSchema -> return getNumberType(schema).toQualifiedName()
      }

      val qualifiedName = when {

         schema.title != null -> QualifiedName(defaultNamespace, schema.title.replace(" ", "_").toCapitalizedWords())
         else -> {
            try {
               val uri = getSchemaIdAsUri(schema, logger) ?: error("Could not parse schema to URI")
               getTypeName(uri, logger)
            } catch (e: Exception) {
               logger.warn("Schema ${schema.id ?: "without a URI"} is not a valid URI, but according to JsonSchema spec it should be.")
               fallback ?: error("No fallback provided, and unable to generate a typeName for schema $schema")
            }
         }
      }
      return qualifiedName.replaceIllegalCharacters()
   }
}

private fun Schema.resolvesEqualTo(other: Schema): Boolean {
   if (this == other) {
      return true
   }
   val resolvedThis = this.resolve()
   val resolvedThat = other.resolve()

   // If both schemas are StringSchema (for example), but only
   // differ in validation rules (for example), then for now,
   // we consider them resolving equal.
   // IN future, we may wish to be more strict about this.
   val schemaTypesThatAreEqualByTypeOnly = listOf(
      BooleanSchema::class,
      EmptySchema::class,
      FalseSchema::class,
      NullSchema::class,
      NumberSchema::class,
      StringSchema::class
   )
   return when {
      resolvedThis == resolvedThat -> true
      resolvedThis::class == resolvedThat::class && schemaTypesThatAreEqualByTypeOnly.contains(resolvedThis::class) -> true
      else -> false
   }
}

private fun Schema.resolve(): Schema {
   return if (this is ReferenceSchema) {
      this.referredSchema
   } else {
      this
   }
}

private fun List<String>.removeEmpties(): List<String> {
   return this.filter { it.isNotBlank() && it.isNotEmpty() }
}
