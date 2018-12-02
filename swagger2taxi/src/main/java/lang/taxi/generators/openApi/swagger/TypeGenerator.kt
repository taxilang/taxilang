package lang.taxi.generators.openApi.swagger

import lang.taxi.CompilationUnit
import lang.taxi.Type
import lang.taxi.generators.Logger
import lang.taxi.generators.openApi.Utils
import lang.taxi.types.*
import v2.io.swagger.models.*
import v2.io.swagger.models.parameters.AbstractSerializableParameter
import v2.io.swagger.models.properties.*

class SwaggerTypeMapper(val swagger: Swagger, val defaultNamespace: String, private val logger: Logger) {

    private val swaggerPrimitivies: Map<String, AbstractProperty> = listOf(
            BinaryProperty(),
            BooleanProperty(),
            DateProperty(),
            DateTimeProperty(),
            DecimalProperty(),
            DoubleProperty(),
            EmailProperty(),
            FloatProperty(),
            IntegerProperty(),
            LongProperty(),
            StringProperty(),
            UUIDProperty()
    ).associateBy { it.type }

    private val generatedTypes = mutableMapOf<String, Type>()
    fun generateTypes(): Set<Type> {
        if (swagger.definitions == null) {
            return emptySet()
        }
        swagger.definitions.forEach { (name, model) ->
            generatedTypes.put(name, generateType(name, model))

        }

        return generatedTypes.values.toSet()
    }

    fun findType(model: Model): Type {
        return when (model) {
            is RefModel -> findTypeByName(model.simpleRef)
            is ArrayModel -> findArrayType(model)
            else -> TODO()
        }
    }

    fun findType(property: Property): Type {
        return when (property) {
            is RefProperty -> findTypeByName(property.simpleRef)
            else -> TODO()
        }
    }

    private fun findArrayType(model: ArrayModel): Type {
        val arrayMemberType = findType(model.items)
        return ArrayType(arrayMemberType, CompilationUnit.unspecified())
    }

    fun findType(param: AbstractSerializableParameter<*>): Type {
        return if (param.getType() == ArrayProperty.TYPE) {
            getOrGenerateType(param.getItems())
        } else {
            findTypeByName(param.getType())
        }
    }

    fun findTypeByName(name: String): Type {
        return getPrimitiveType(name) ?: getOrGenerateType(name)
    }


    fun getOrGenerateType(property: Property): Type {
        return when (property) {
            is ArrayProperty -> ArrayType(getOrGenerateType(property.items), CompilationUnit.unspecified())
            is RefProperty -> findTypeByName(property.simpleRef)
            else -> getPrimitiveType(property)
                    ?: getOrGenerateType(property.type)
        }
    }

    private fun getOrGenerateType(name: String): Type {
        val qualifiedName = Utils.qualifyTypeNameIfRaw(name, defaultNamespace)
        return generatedTypes.getOrPut(qualifiedName) {
            val model = swagger.definitions[name]
                    ?: error("No definition is present within the swagger file for type $name")
            generateType(qualifiedName, model)
        }
    }


    private fun generateType(name: String, model: Model): Type {
        return when (model) {
            is ModelImpl -> generateType(name, model)
            is ComposedModel -> generateType(name, model)
            is ArrayModel -> generateType(name, model)
            else -> TODO("Model type ${model.javaClass.name} not yet supported")
        }
    }

    private fun generateType(name: String, model: ModelImpl): Type {
        val qualifiedName = Utils.qualifyTypeNameIfRaw(name, defaultNamespace)
        val fields = generateFields(model.properties)
//        val definition = ObjectTypeDefinition(
//
//        )
        // TODO: Compililation Units / sourceCode linking
        val typeDef = ObjectTypeDefinition(fields.toSet(), compilationUnit = CompilationUnit.unspecified())
        return ObjectType(qualifiedName, typeDef)
    }

    private fun generateFields(properties: Map<String, Property>): Set<Field> {
        return properties.map { (name, property) -> generateField(name, property) }.toSet()
    }

    private fun generateField(name: String, property: Property): Field {
        return Field(name, getOrGenerateType(property), nullable = !property.required)
    }


    private fun generateType(name: String, model: ComposedModel): Type {
        val qualifiedName = Utils.qualifyTypeNameIfRaw(name, defaultNamespace)
        val interfaces: Map<RefModel, ObjectType> = model.interfaces?.map { it to getOrGenerateType(it.simpleRef) as ObjectType }?.toMap()
                ?: emptyMap()

        // TODO : This could be significantly over-simplifying.
        val fields: Set<Field> = model.allOf.filterNot { interfaces.containsKey(it) }
                .flatMap { generateFields(it.properties) }.toSet()
        return ObjectType(qualifiedName, ObjectTypeDefinition(
                fields,
                inheritsFrom = interfaces.values.toSet(),
                compilationUnit = CompilationUnit.unspecified() // TODO
        ))
    }

    private fun generateType(name: String, model: ArrayModel): Type {
        val collectionType = getOrGenerateType(model.items)
        return ArrayType(collectionType, CompilationUnit.unspecified())
    }

    private fun getPrimitiveType(typeName: String): PrimitiveType? {
        return swaggerPrimitivies[typeName]?.let { getPrimitiveType(it) }
    }

    private fun getPrimitiveType(property: Property): PrimitiveType? {
        return when (property) {
            is BooleanProperty -> PrimitiveType.BOOLEAN
            is BinaryProperty -> PrimitiveType.BOOLEAN
            is DateProperty -> PrimitiveType.LOCAL_DATE
            is DateTimeProperty -> PrimitiveType.DATE_TIME
            is DecimalProperty -> PrimitiveType.DECIMAL
            is DoubleProperty -> PrimitiveType.DECIMAL
            is EmailProperty -> PrimitiveType.STRING
            is FloatProperty -> PrimitiveType.DECIMAL
            is BaseIntegerProperty -> PrimitiveType.INTEGER
            is IntegerProperty -> PrimitiveType.INTEGER
            is LongProperty -> PrimitiveType.INTEGER
            is StringProperty -> PrimitiveType.STRING
            is UUIDProperty -> PrimitiveType.STRING
            is MapProperty -> PrimitiveType.ANY
            else -> null
        }
    }

}
