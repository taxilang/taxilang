package lang.taxi

import com.google.common.collect.Multimaps
import lang.taxi.services.Service
import lang.taxi.types.Annotation
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType
import org.antlr.v4.runtime.ParserRuleContext

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

interface Compiled {
    val compilationUnits: List<CompilationUnit>
}

data class CompilationUnit(val ruleContext: ParserRuleContext?,
                           val source: SourceCode) {
    companion object {
        fun unspecified(): CompilationUnit {
            return CompilationUnit(ruleContext = null, source = SourceCode.unspecified())
        }

        fun ofSource(source: SourceCode): CompilationUnit {
            return CompilationUnit(null, source)
        }

        fun of(typeRule: ParserRuleContext): CompilationUnit {
            return CompilationUnit(typeRule, typeRule.source())
        }
    }
}

data class SourceCode(
        val origin: String,
        val content: String
) {
    companion object {
        fun unspecified(): SourceCode = SourceCode("Not specified", "")
    }
}


interface Type : Named, Compiled

/**
 * A type that can be declared by users explicity.
 * eg:  Object type, Enum type.
 * ArrayType is excluded (as arrays are primitive, and the inner
 * type will be a UserType)
 */
interface UserType<TDef : TypeDefinition, TExt : TypeDefinition> : Type {
    var definition: TDef?
    val extensions: MutableList<TExt>

    val isDefined: Boolean
        get() {
            return this.definition != null
        }

    override val compilationUnits: List<CompilationUnit>
        get() = (this.extensions.map { it.compilationUnit } + this.definition?.compilationUnit).filterNotNull()


}

interface TypeDefinition {
    val compilationUnit: CompilationUnit
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
    private val equality = Equality(this, TaxiDocument::types, TaxiDocument::services)
    private val typeMap = types.associateBy { it.qualifiedName }
    private val servicesMap = services.associateBy { it.qualifiedName }
    fun type(name: String): Type {
        return typeMap[name]!!
    }

    fun containsType(typeName: String) = typeMap.containsKey(typeName)
    fun containsService(serviceName: String) = servicesMap.containsKey(serviceName)

    override fun hashCode() = equality.hash()
    override fun equals(other: Any?) = equality.isEqualTo(other)

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

    fun merge(other: TaxiDocument): TaxiDocument {
        val duplicates: List<Named> = collectDuplicateTypes(other) + collectDuplicateServices(other)
        if (duplicates.isNotEmpty()) {
            val namesInError = duplicates.joinToString { it.qualifiedName }
            throw IllegalArgumentException("Attemped to redefined types with conflicting definitions: $namesInError")
        }
        return TaxiDocument(this.types + other.types,
                this.services + other.services)
    }

    private fun collectDuplicateServices(other: TaxiDocument): List<Service> {
        val duplicateServices = this.services.filter { other.containsService(it.qualifiedName) }
        return duplicateServices.filter { it != other.service(it.qualifiedName) }

    }

    private fun collectDuplicateTypes(other: TaxiDocument): List<Type> {
        val duplicateTypes = this.types.filter { other.containsType(it.qualifiedName) }
        return duplicateTypes.filter { it != other.type(it.qualifiedName) }

    }
}
