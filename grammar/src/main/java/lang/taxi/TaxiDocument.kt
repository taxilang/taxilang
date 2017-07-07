package lang.taxi

import lang.taxi.services.Service
import lang.taxi.types.Annotation
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType

interface Type {
    val qualifiedName: String
}

/**
 * A type that can be declared by users explicity.
 * eg:  Object type, Enum type.
 * ArrayType is excluded (as arrays are primitive, and the inner
 * type will be a UserType)
 */
interface UserType<TDef, TExt> : Type {
    var definition: TDef?
    val extensions: MutableList<TExt>

    val isDefined: Boolean
        get() {
            return this.definition != null
        }
}

interface Annotatable {
    val annotations: List<Annotation>
}

fun List<Annotatable>.annotations(): List<Annotation> {
    return this.flatMap { it.annotations }
}

data class TaxiDocument(val namespace: String?,
                        val types: List<Type>,
                        val services: List<Service>
) {
    private val typeMap = types.associateBy { it.qualifiedName }
    private val servicesMap = services.associateBy { it.qualifiedName }
    fun type(name: String): Type {
        return typeMap[name]!!
    }

    fun objectType(name: String): ObjectType {
        return type(name) as ObjectType
    }

    fun enumType(qualifiedName: String): EnumType {
        return type(qualifiedName) as EnumType
    }

    fun service(qualifiedName: String): Service {
        return servicesMap[qualifiedName]!!
    }
}
