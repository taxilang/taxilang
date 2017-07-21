package lang.taxi

import com.google.common.collect.Multimaps
import lang.taxi.services.Service
import lang.taxi.types.Annotation
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType
import java.util.*

data class QualifiedName(val namespace: String, val typeName: String) {
    companion object {
        private val nativeNamespaces = listOf("lang.taxi")
        fun from(value: String): QualifiedName {
            val parts = value.split(".")
            val typeName = parts.last()
            val namespace = parts.dropLast(1).joinToString(".")
            return QualifiedName(namespace, typeName)
        }
    }

    fun qualifiedRelativeTo(otherNamespace: String): String {
        if (this.namespace == otherNamespace) {
            return typeName
        }
        if (nativeNamespaces.contains(this.namespace)) {
            return typeName
        }
        return "$namespace.$typeName"
    }
}

interface Named {
    val qualifiedName: String

    fun toQualifiedName(): QualifiedName {
        return QualifiedName.from(qualifiedName)
    }
}

interface Type : Named

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

class NamespacedTaxiDocument(val namespace: String,
                             types: List<Type>,
                             services: List<Service>) : TaxiDocument(types, services)

open class TaxiDocument(val types: List<Type>,
                        val services: List<Service>
) {
    private val typeMap = types.associateBy { it.qualifiedName }
    private val servicesMap = services.associateBy { it.qualifiedName }
    fun type(name: String): Type {
        return typeMap[name]!!
    }

    override fun hashCode(): Int {
        return Objects.hash(typeMap,servicesMap)
    }
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TaxiDocument)
            return false
        return Objects.equals(typeMap, other.typeMap)
                && Objects.equals(servicesMap, other.servicesMap)
    }

    fun toNamespacedDocs(): List<NamespacedTaxiDocument> {
        val typesByNamespace = Multimaps.index(types, { it!!.toQualifiedName().namespace })
        val servicesByNamespace = Multimaps.index(services, { it!!.toQualifiedName().namespace })
        val namespaces = typesByNamespace.keySet() + servicesByNamespace.keySet()

        return namespaces.map { namespace ->
            NamespacedTaxiDocument(namespace,
                    types = typesByNamespace.get(namespace) ?: emptyList(),
                    services = servicesByNamespace.get(namespace) ?: emptyList())
        }
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
