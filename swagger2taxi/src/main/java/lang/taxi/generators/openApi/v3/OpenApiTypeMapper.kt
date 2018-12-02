package lang.taxi.generators.openApi.v3

import io.swagger.oas.models.OpenAPI
import io.swagger.oas.models.media.*
import lang.taxi.CompilationUnit
import lang.taxi.Type
import lang.taxi.generators.Logger
import lang.taxi.generators.openApi.Utils
import lang.taxi.types.*

class OpenApiTypeMapper(val api: OpenAPI, val defaultNamespace: String, private val logger: Logger) {

    private val generatedTypes = mutableMapOf<String, Type>()
    fun generateTypes(): Set<Type> {
        if (api.components == null) {
            return emptySet()
        }
        api.components.schemas.map { (name, schema) ->
            generatedTypes[name] = generateType(name, schema)
        }
        return generatedTypes.values.toSet()
    }

    private fun generateType(name: String, schema: Schema<*>): Type {
        return when (schema) {
            is ArraySchema -> generateArrayType(name, schema)
            else -> generateObjectType(name, schema)
        }
    }

    private fun generateArrayType(name: String, schema: ArraySchema): Type {
        val arrayInnerType = getOrGenerateType(schema.items)
        return ArrayType(arrayInnerType, CompilationUnit.unspecified());
    }

    private fun generateObjectType(name: String, schema: Schema<*>): Type {
        val requiredFields = schema.required ?: emptyList()
        val qualifiedName = Utils.qualifyTypeNameIfRaw(name, defaultNamespace)
        val properties = schema.properties ?: emptyMap()
        val fields = properties.map { (name, schema) ->
            generateField(name, schema, requiredFields.contains(name))
        }
        val typeDef = ObjectTypeDefinition(fields.toSet(), compilationUnit = CompilationUnit.unspecified())
        return ObjectType(qualifiedName, typeDef)
    }

    private fun generateField(name: String, schema: Schema<*>, required: Boolean): Field {
        return Field(name, getOrGenerateType(schema), nullable = required)
    }

    private fun getOrGenerateType(schema: Schema<*>, anonymousTypeNamePartial: String? = null): Type {
        return getPrimitiveType(schema)
                ?: generatedTypes[schema.type]
                ?: getTypeFromRef(schema.`$ref`)
                ?: generateType(typeNameFromSchema(schema, anonymousTypeNamePartial), schema)
    }

    private fun typeNameFromSchema(schema: Schema<*>, anonymousTypeNamePartial: String?): String {
        if (schema.type != null) return schema.type
        if (anonymousTypeNamePartial != null) return "AnonymousType$anonymousTypeNamePartial"
        error("Type name is not defined, and no naming for anonymous type was provided")
    }

    private fun getTypeFromRef(typeRef: String?): Type? {
        if (typeRef == null) return null;
        val typeName = typeRef.split("/").last()
        val type = this.generatedTypes[typeName]
        if (type == null) {
            logger.error("Schema has pointer to non-existent type $typeRef")
            return null
        }
        return type
    }

    private fun getPrimitiveType(schema: Schema<*>): PrimitiveType? {
        return when (schema) {
            is BooleanSchema -> PrimitiveType.BOOLEAN
            is DateSchema -> PrimitiveType.LOCAL_DATE
            is DateTimeSchema -> PrimitiveType.DATE_TIME
            is EmailSchema -> PrimitiveType.STRING
            is IntegerSchema -> PrimitiveType.INTEGER
            is NumberSchema -> PrimitiveType.DECIMAL
            is StringSchema -> PrimitiveType.STRING
            is UUIDSchema -> PrimitiveType.STRING
            is ObjectSchema -> PrimitiveType.ANY
            else -> null
        }
    }

    fun findType(schema: Schema<*>, anonymousTypeNamePartial: String? = null): Type {
        return getOrGenerateType(schema, anonymousTypeNamePartial)
    }

}