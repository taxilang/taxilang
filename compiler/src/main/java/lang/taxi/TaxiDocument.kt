package lang.taxi

import com.google.common.collect.Multimaps
import lang.taxi.services.Service
import lang.taxi.types.Annotation
import lang.taxi.types.EnumType
import lang.taxi.types.ObjectType
import org.antlr.v4.runtime.ParserRuleContext

/**
 * A series of named attributes on an entity that describe a path
 * eg foo.baz.bar
 */
data class AttributePath(val parts: List<String>) {
    constructor(qualifiedName: TaxiParser.QualifiedNameContext) : this(qualifiedName.Identifier().map { it.text })

    companion object {
        fun from(value: String): AttributePath {
            return AttributePath(value.split("."))
        }
    }

    val path = parts.joinToString(".")

    override fun toString() = "AttributePath ($path)"
}

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

    override fun toString(): String {
        return if (namespace.isNotEmpty()) {
            "${namespace}.$typeName"
        } else {
            typeName
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

typealias ErrorMessage = String

/**
 * A type that can be declared by users explicity.
 * eg:  Object type, Enum type.
 * ArrayType is excluded (as arrays are primitive, and the inner
 * type will be a UserType)
 */
interface UserType<TDef : TypeDefinition, TExt : TypeDefinition> : Type {
    var definition: TDef?

    val extensions: List<TExt>

    fun addExtension(extension: TExt): ErrorMessage?

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
                             types: Set<Type>,
                             services: Set<Service>) : TaxiDocument(types, services)

// Note:  Changed types & services from List<> to Set<>
// as ordering shouldn't matter, only content.
// However, I suspect there was a reason these were Lists, so leaving this note here to remind me
open class TaxiDocument(val types: Set<Type>,
                        val services: Set<Service>
) {
    private val equality = Equality(this, TaxiDocument::types, TaxiDocument::services)
    private val typeMap = types.associateBy { it.qualifiedName }
    private val servicesMap = services.associateBy { it.qualifiedName }
    fun type(name: String): Type {
        return typeMap[name] ?: throw error("No type named $name defined")
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
                    types = typesByNamespace.get(namespace)?.toSet() ?: emptySet(),
                    services = servicesByNamespace.get(namespace)?.toSet() ?: emptySet())
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

    private fun Iterable<CompilationUnit>.declarationSites(): String {
        return this.joinToString { it.source.origin }
    }

    fun merge(other: TaxiDocument): TaxiDocument {
        val conflicts: List<Named> = collectConflictingTypes(other) + collectDuplicateServices(other)
        val errors = conflicts.map {
            val site1 = this.type(it.qualifiedName).compilationUnits.declarationSites()
            val site2 = other.type(it.qualifiedName).compilationUnits.declarationSites()
            DocumentStrucutreError("Attempted to redefine types with conflicting definition - ${it.qualifiedName} is defined in the following locations: $site1 which conflicts with the definition at $site2")
        }
        if (errors.isNotEmpty()) {
            throw DocumentMalformedException(errors)
        }

        // TODO : We should be merging where there are extensions in otherwise
        // equal type definitions.
        val duplicateNames = this.types.filter { other.containsType(it.qualifiedName) }.map { it.qualifiedName }

        return TaxiDocument(this.types + other.types.filterNot { duplicateNames.contains(it.qualifiedName) },
                this.services + other.services)
    }

    private fun collectDuplicateServices(other: TaxiDocument): List<Service> {
        val duplicateServices = this.services.filter { other.containsService(it.qualifiedName) }
        return duplicateServices.filter { it != other.service(it.qualifiedName) }

    }

    private fun collectConflictingTypes(other: TaxiDocument): List<Type> {
        val duplicateTypes = this.types.filter { other.containsType(it.qualifiedName) }
        // TODO : This should consider extensions.
        // If the underlying type definitions are the same, but one adds extensions,
        // that's valid.
        return duplicateTypes.filter { it != other.type(it.qualifiedName) }

    }
}
