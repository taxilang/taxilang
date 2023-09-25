package lang.taxi.generators.openApi.v3


import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.*
import lang.taxi.generators.NamingUtils
import lang.taxi.generators.NamingUtils.replaceIllegalCharacters
import lang.taxi.types.*

class OpenApiTypeMapper(private val api: OpenAPI, val defaultNamespace: String) {

   private val _generatedTypes = mutableMapOf<QualifiedName, Type>()

   val generatedTypes: Set<Type> get() = _generatedTypes.values.toSet()

   fun generateTypes() {
      api.components?.schemas?.forEach { (name, schema) ->
         generateNamedTypeRecursively(schema, qualify(name), listOf(Modifier.CLOSED))
      }
   }

   private fun generateNamedTypeRecursively(
      schema: Schema<*>,
      name: QualifiedName,
      modifiers: List<Modifier>,
      /**
       * A list of names that are explicity inherited (eg., for mapping common
       * types from a main taxonomy)
       * These are not validated
       */
      declaredSupertypes: List<String> = emptyList()
   ): Type {
      val taxiExtension = schema.taxiExtension
      return if (taxiExtension == null) {
         generate(name, schema, modifiers, declaredSupertypes)
      } else {
         val taxiExtName = qualify(taxiExtension.name)
         if (taxiExtension.shouldGenerateFor(schema)) {
            generate(taxiExtName, schema, modifiers, declaredSupertypes)
         } else {
            _generatedTypes.getOrPut(taxiExtName) {
               UnresolvedImportedType(taxiExtName.fullyQualifiedName)
            }
         }
      }
   }

   private fun generate(name: QualifiedName, schema: Schema<*>, modifiers: List<Modifier>, declaredSupertypes: List<String>) =
      if (schema.isModel()) {
         generateModel(name, schema, modifiers, declaredSupertypes)
      } else {
         val supertype = toType(schema, name.typeName, modifiers)
         generateType(name, supertype, declaredSupertypes)
      }

   fun generateUnnamedTypeRecursively(
      schema: Schema<*>,
      context: String,
      modifiers: List<Modifier>
   ): Type =
      getTypeFromExtensions(schema, modifiers) ?: toType(schema, context, modifiers) ?: schema.`$ref`?.getTypeFromRef(modifiers)
      ?: generateModelIfAppropriate(schema, context, modifiers) ?: PrimitiveType.ANY

   private fun getTypeFromExtensions(schema: Schema<*>, modifiers: List<Modifier>): Type? {
      val explicitTaxiType = schema.taxiExtension
      return if (explicitTaxiType != null) {
         generateNamedTypeRecursively(schema, qualify(explicitTaxiType.name), modifiers = modifiers, declaredSupertypes = explicitTaxiType.inherits)
      } else {
         null
      }
   }

   private fun generateModelIfAppropriate(schema: Schema<*>, context: String, modifiers: List<Modifier>): Type? =
      if (schema.isModel()) {
         val name = anonymousModelName(context)
         generateModel(name, schema, modifiers)
      } else null

   private fun toType(schema: Schema<*>, context: String, modifiers: List<Modifier>) =
      primitiveTypeFor(schema) ?: intermediateTypeFor(schema) ?: if (schema is ArraySchema) {
         makeArrayType(schema, context + "Element", modifiers)
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
         generateType(formatTypeQualifiedName, PrimitiveType.STRING)
      }

      else -> null
   }

   private fun generateType(
      taxiExtName: QualifiedName,
      supertype: Type?,
      declaredSupertypes: List<String> = emptyList()
   ) = _generatedTypes.getOrPut(taxiExtName) {
      ObjectType(
         taxiExtName.toString(),
         ObjectTypeDefinition(
            inheritsFrom = setOfNotNull(supertype) + declaredSupertypes.map { UnresolvedImportedType(it) },
            compilationUnit = CompilationUnit.unspecified()
         )
      )
   }

   private fun makeArrayType(
      schema: ArraySchema, context: String,
      modifiers: List<Modifier>
   ): ArrayType {
      val innerType = generateUnnamedTypeRecursively(schema.items, context, modifiers)
      return ArrayType.of(innerType)
   }

   private fun generateModel(
      name: QualifiedName,
      schema: Schema<*>,
      modifiers: List<Modifier>,
      declaredSupertypes: List<String> = emptyList()
   ) = _generatedTypes.getOrPut(name) {
      // This allows us to support recursion - the undefined type will prevent us getting into an endless loop
      _generatedTypes[name] = ObjectType.undefined(name.fullyQualifiedName)
      if (schema is ComposedSchema) {
         val allOf = schema.allOf ?: emptyList()
         val inherits = allOf.mapNotNull { it.`$ref`?.getTypeFromRef(modifiers) }.toSet() + declaredSupertypes.map { UnresolvedImportedType(it) }.toSet()
         // If requiredFields is present, it contributes to the definition of nullable.
         // However, if requiredFields is omitted we only consider the nullable attribute of fields
         val requiredFields = if (allOf.any { it.required != null }) {
            allOf.flatMap { it.required ?: emptyList() }
         } else {
            null
         }
         val properties =
            allOf.flatMap { it.properties?.toList() ?: emptyList() }
               .toMap()
         makeModel(
            name = name,
            inherits = inherits,
            properties = properties,
            requiredFields = requiredFields,
            description = schema.description,
            modifiers = modifiers
         )
      } else {
         makeModel(
            name = name,
            inherits = emptySet(),
            properties = schema.properties ?: emptyMap(),
            requiredFields = schema.required,
            description = schema.description,
            modifiers = modifiers
         )
      }
   }

   private fun makeModel(
      name: QualifiedName,
      inherits: Set<Type>,
      properties: Map<String, Schema<Any>>,
      // Nullable, as the requiredFields concept is optional in OpenApi,
      // so we allow null to indicate "defer to the property schema".
      // See order of precedence discussed on generateField
      requiredFields: List<String>?,
      description: String?,
      modifiers: List<Modifier>
   ): ObjectType {
      val fields = properties.map { (propName, schema) ->
         generateField(
            name = propName,
            schema = schema,
            explicitlyRequired = requiredFields?.contains(propName),
            parent = name,
         )
      }
      val typeDef = ObjectTypeDefinition(
         inheritsFrom = inherits,
         fields = fields.toSet(),
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = description,
         modifiers = modifiers
      )
      return ObjectType(name.fullyQualifiedName, typeDef)
   }

   /**
    * explicitlyRequired indicates if the field was declared as "required" in the OpenApi spec.
    * It's nullable, as declaring required fields is optional.
    * For determining nullability, we consider the following order of precedence (highest to lowest):
    *  - is the field marked nullable?
    *  - were required fields declared, and - if so, was this field listed?
    *  - default to not nullable.
    *
    */
   private fun generateField(
      name: String,
      schema: Schema<*>,
      explicitlyRequired: Boolean?,
      parent: QualifiedName
   ): Field {
      val legalName = name.replaceIllegalCharacters()
      val nullable = schema.nullable ?: explicitlyRequired?.not() ?: false
      return Field(
         name = legalName,
         type = generateUnnamedTypeRecursively(schema, context = parent.typeName + legalName.capitalize(), listOf(Modifier.CLOSED)),
         nullable = nullable,
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = schema.description,
      )
   }

   private fun String.getTypeFromRef(modifiers: List<Modifier>): Type {
      val (name, schema) = RefEvaluator.navigate(this@OpenApiTypeMapper.api, this)
      return generateNamedTypeRecursively(schema, qualify(name), modifiers)
   }

   private fun anonymousModelName(context: String): QualifiedName {
      val typeName =
         if (context.startsWith("AnonymousType")) context
         else "AnonymousType${context.capitalize()}"
      return qualify(typeName)
   }

   private fun qualify(name: String) =
      NamingUtils.qualifyTypeNameIfRaw(name, defaultNamespace)
}

fun Schema<*>.isModel() = (this is ComposedSchema && oneOf == null && anyOf == null) || !properties.isNullOrEmpty()
fun Schema<*>.isType() = !isModel()
