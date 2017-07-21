package lang.taxi.types

import com.google.common.base.Objects
import lang.taxi.Annotatable
import lang.taxi.Type
import lang.taxi.UserType
import lang.taxi.annotations

data class FieldExtension(val name: String, override val annotations: List<Annotation>) : Annotatable
data class ObjectTypeExtension(val annotations: List<Annotation> = emptyList(),
                               val fieldExtensions: List<FieldExtension> = emptyList()) {
    fun fieldExtensions(name: String): List<FieldExtension> {
        return this.fieldExtensions.filter { it.name == name }
    }
}

data class ObjectTypeDefinition(val fields: List<Field> = emptyList(), val annotations: List<Annotation> = emptyList()) {
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

data class ObjectType(
        override val qualifiedName: String,
        override var definition: ObjectTypeDefinition?,
        override val extensions: MutableList<ObjectTypeExtension> = mutableListOf()
) : UserType<ObjectTypeDefinition, ObjectTypeExtension>, Annotatable {
    constructor(qualifiedName: String, fields: List<Field>) : this(qualifiedName, ObjectTypeDefinition(fields))

    companion object {
        fun undefined(name: String): ObjectType {
            return ObjectType(name, definition = null)
        }
    }

    override fun toString(): String {
        return qualifiedName
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
        val annotations: List<Annotation> = emptyList()
)

