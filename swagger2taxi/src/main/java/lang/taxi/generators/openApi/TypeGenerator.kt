package lang.taxi.generators.openApi

import lang.taxi.CompilationUnit
import lang.taxi.Type
import lang.taxi.types.*
import v2.io.swagger.models.*
import v2.io.swagger.models.parameters.AbstractSerializableParameter
import v2.io.swagger.models.properties.*

class SwaggerTypeMapper(val swagger: Swagger) {

    private val swaggerPrimitivies: Map<String, AbstractProperty> = listOf(
            BinaryProperty(),
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
        swagger.definitions.forEach { (name, model) ->
            generatedTypes.put(name, generateType(name, model))

        }

        return generatedTypes.values.toSet()
    }

    fun findType(model: Model): Type {
        return when (model) {
            is RefModel -> findTypeByName(model.simpleRef)
            else -> TODO()
        }
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
        return generatedTypes.getOrPut(name) {
            val model = swagger.definitions[name]
                    ?: error("No definition is present within the swagger file for type $name")
            generateType(name, model)
        }
    }

    private fun generateType(name: String, model: Model): Type {
        return when (model) {
            is ModelImpl -> generateType(name, model)
            is ComposedModel -> generateType(name, model)
            else -> TODO("Model type ${model.javaClass.name} not yet supported")
        }
    }

    private fun generateType(name: String, model: ModelImpl): Type {
        val fields = generateFields(model.properties)
//        val definition = ObjectTypeDefinition(
//
//        )
        // TODO: Compililation Units / sourceCode linking
        val typeDef = ObjectTypeDefinition(fields.toSet(), compilationUnit = CompilationUnit.unspecified())
        return ObjectType(name, typeDef)
    }

    private fun generateFields(properties: Map<String, Property>): Set<Field> {
        return properties.map { (name, property) -> generateField(name, property) }.toSet()
    }

    private fun generateField(name: String, property: Property): Field {
        return Field(name, getOrGenerateType(property), nullable = !property.required)
    }


    private fun generateType(name: String, model: ComposedModel): Type {
        val interfaces: Map<RefModel, ObjectType> = model.interfaces?.map { it to getOrGenerateType(it.simpleRef) as ObjectType }?.toMap()
                ?: emptyMap()

        // TODO : This could be significantly over-simplifying.
        val fields: Set<Field> = model.allOf.filterNot { interfaces.containsKey(it) }
                .flatMap { generateFields(it.properties) }.toSet()
        return ObjectType(name, ObjectTypeDefinition(
                fields,
                inheritsFrom = interfaces.values.toSet(),
                compilationUnit = CompilationUnit.unspecified() // TODO
        ))
    }

    private fun getPrimitiveType(typeName: String): PrimitiveType? {
        return swaggerPrimitivies[typeName]?.let { getPrimitiveType(it) }
    }

    private fun getPrimitiveType(property: Property): PrimitiveType? {
        return when (property) {
            is BinaryProperty -> PrimitiveType.BOOLEAN
            is DateProperty -> PrimitiveType.LOCAL_DATE
            is DateTimeProperty -> PrimitiveType.DATE_TIME
            is DecimalProperty -> PrimitiveType.DECIMAL
            is DoubleProperty -> PrimitiveType.DECIMAL
            is EmailProperty -> PrimitiveType.STRING
            is FloatProperty -> PrimitiveType.DECIMAL
            is IntegerProperty -> PrimitiveType.INTEGER
            is LongProperty -> PrimitiveType.INTEGER
            is StringProperty -> PrimitiveType.STRING
            is UUIDProperty -> PrimitiveType.STRING
            else -> null
        }
    }

}
