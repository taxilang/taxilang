package lang.taxi.types

import com.google.common.base.Objects
import lang.taxi.*
import lang.taxi.services.Constraint

data class FieldExtension(val name: String, override val annotations: List<Annotation>) : Annotatable
data class ObjectTypeExtension(val annotations: List<Annotation> = emptyList(),
                               val fieldExtensions: List<FieldExtension> = emptyList(),
                               val source:SourceCode = SourceCode.unspecified()) {
    fun fieldExtensions(name: String): List<FieldExtension> {
        return this.fieldExtensions.filter { it.name == name }
    }
}

data class ObjectTypeDefinition(val fields: List<Field> = emptyList(), val annotations: List<Annotation> = emptyList(), val modifiers: List<Modifier> = emptyList(), val source:SourceCode = SourceCode.unspecified()) {
    private fun fieldNames() = fields.map { it.name to it.type.qualifiedName }
    override fun hashCode(): Int {
        return Objects.hashCode(fieldNames(), annotations)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ObjectTypeDefinition)
            return false
        return java.util.Objects.equals(this.fieldNames(), other.fieldNames()) && Objects.equal(annotations, other.annotations)
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

    override fun toString(): String {
        return qualifiedName
    }

    override val sources: List<SourceCode>
        get() = (this.extensions.map { it.source } + this.definition?.source).filterNotNull()

    val modifiers: List<Modifier>
        get() {
            return this.definition?.modifiers ?: emptyList()
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

    fun field(name: String): Field = fields.first { it.name == name }
    fun annotation(name: String): Annotation = annotations.first { it.name == name }

}

data class Annotation(val name: String, val parameters: Map<String, Any?> = emptyMap())
data class Field(
        val name: String,
        val type: Type,
        val nullable: Boolean = false,
        val annotations: List<Annotation> = emptyList(),
        val constraints: List<Constraint> = emptyList()
)

