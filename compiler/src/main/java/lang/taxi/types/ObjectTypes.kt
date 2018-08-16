package lang.taxi.types

import lang.taxi.*
import lang.taxi.services.Constraint
import kotlin.reflect.KProperty1

data class FieldExtension(val name: String, override val annotations: List<Annotation>) : Annotatable
data class ObjectTypeExtension(val annotations: List<Annotation> = emptyList(),
                               val fieldExtensions: List<FieldExtension> = emptyList(),
                               override val compilationUnit: CompilationUnit) : TypeDefinition {
    fun fieldExtensions(name: String): List<FieldExtension> {
        return this.fieldExtensions.filter { it.name == name }
    }
}

data class ObjectTypeDefinition(
        val fields: Set<Field> = emptySet(),
        val annotations: Set<Annotation> = emptySet(),
        val modifiers: List<Modifier> = emptyList(),
        val inheritsFrom: Set<ObjectType> = emptySet(),
        override val compilationUnit: CompilationUnit
) : TypeDefinition {
    private val equality = Equality(this, ObjectTypeDefinition::fields.toSet(), ObjectTypeDefinition::annotations.toSet(), ObjectTypeDefinition::modifiers.toSet())
    override fun equals(other: Any?) = equality.isEqualTo(other)
    override fun hashCode(): Int = equality.hash()
}

internal fun <T, R> KProperty1<T, Collection<R>>.toSet(): T.() -> Set<R>? {
    val prop = this
    return {
        prop.get(this).toSet()
    }
}

enum class Modifier(val token: String) {

    // A Parameter type indicates that the object
// is used when constructing requests,
// and that frameworks should freely construct
// these types based on known values.
    PARAMETER_TYPE("parameter");

    companion object {
        fun fromToken(token: String): Modifier {
            return Modifier.values().first { it.token == token }
        }
    }

}

data class ObjectType(
        override val qualifiedName: String,
        override var definition: ObjectTypeDefinition?,
        override val extensions: MutableList<ObjectTypeExtension> = mutableListOf()
) : UserType<ObjectTypeDefinition, ObjectTypeExtension>, Annotatable {
    companion object {
        fun undefined(name: String): ObjectType {
            return ObjectType(name, definition = null)
        }
    }

    val inheritsFrom: Set<ObjectType>
        get() {
            return definition?.inheritsFrom ?: emptySet()
        }

    val inheritsFromNames: List<String>
        get() {
            return inheritsFrom.map { it.qualifiedName }
        }

    override fun toString(): String {
        return qualifiedName
    }

    val modifiers: List<Modifier>
        get() {
            return this.definition?.modifiers ?: emptyList()
        }

    private fun getInheritanceGraph(typesToExclude: Set<ObjectType> = emptySet()): Set<ObjectType> {
        val allExcludedTypes = typesToExclude + setOf(this)
        return this.inheritsFrom
                .flatMap { inheritedType ->
                    if (!typesToExclude.contains(inheritedType))
                        setOf(inheritedType) + inheritedType.getInheritanceGraph(allExcludedTypes)
                    else emptySet()
                }.toSet()
    }

    val inheritedFields: List<Field>
        get() {
            return getInheritanceGraph().flatMap { it.fields }
        }

    val allFields: List<Field>
        get() {
            return inheritedFields + fields
        }

    val fields: List<Field>
        get() {
            return this.definition?.fields?.map { field ->
                val collatedAnnotations = field.annotations + fieldExtensions(field.name).annotations()
                field.copy(annotations = collatedAnnotations)
            } ?: emptyList()
        }

    override val annotations: List<Annotation>
        get() {
            val collatedAnnotations = this.extensions.flatMap { it.annotations }.toMutableList()
            definition?.annotations?.forEach { collatedAnnotations.add(it) }
            return collatedAnnotations.toList()
        }

    private fun fieldExtensions(fieldName: String): List<FieldExtension> {
        return this.extensions.flatMap { it.fieldExtensions(fieldName) }
    }

    fun field(name: String): Field = allFields.first { it.name == name }
    fun annotation(name: String): Annotation = annotations.first { it.name == name }

}

data class Annotation(val name: String, val parameters: Map<String, Any?> = emptyMap())
data class Field(
        val name: String,
        val type: Type,
        val nullable: Boolean = false,
        val annotations: List<Annotation> = emptyList(),
        val constraints: List<Constraint> = emptyList()
) {

    // For equality - don't compare on the type (as this can cause stackOverflow when the type is an Object type)
    private val typeName = type.qualifiedName
    private val equality = Equality(this, Field::name, Field::typeName, Field::nullable, Field::annotations, Field::constraints)

    override fun equals(other: Any?) = equality.isEqualTo(other)
    override fun hashCode(): Int = equality.hash()


}
