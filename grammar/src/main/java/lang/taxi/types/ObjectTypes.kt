package lang.taxi.types

import lang.taxi.Type
import lang.taxi.TypeProxy
import lang.taxi.TypeSystem

data class FieldExtension(val name: String, val annotations: List<Annotation>)
data class ObjectTypeExtension(val annotations: MutableList<Annotation> = mutableListOf(),
                               val fieldExtensions: MutableList<FieldExtension> = mutableListOf()) {
   fun fieldExtensions(name: String): List<FieldExtension> {
      return this.fieldExtensions.filter { it.name == name }
   }
}

data class ObjectTypeDefinition(val fields: List<Field>)

data class ObjectType(
   override val qualifiedName: String,
   var definition: ObjectTypeDefinition?,
   val extensions: MutableList<ObjectTypeExtension> = mutableListOf()
) : Type {
   constructor(qualifiedName: String, fields: List<Field>) : this(qualifiedName, ObjectTypeDefinition(fields))

   companion object {
      fun undefined(name: String): ObjectType {
         return ObjectType(name, definition = null)
      }
   }

   override fun toString(): String {
      return qualifiedName
   }
   val isDefined: Boolean
      get() {
         return this.definition != null
      }
   val fields: List<Field>
      get() {
         return this.definition?.fields?.map { field ->
            val collatedAnnotations = field.annotations + fieldExtensions(field.name).annotations
            field.copy(annotations = collatedAnnotations)
         } ?: emptyList()
      }

   private fun fieldExtensions(fieldName: String): List<FieldExtension> {
      return this.extensions.flatMap { it.fieldExtensions(fieldName) }
   }

   fun field(name: String): Field = fields.first { it.name == name }
}

private val List<FieldExtension>.annotations: List<Annotation>
   get() {
      return this.flatMap { it.annotations }
   }

data class Annotation(val name: String)
data class Field(
   val name: String,
   val type: Type,
   val nullable: Boolean = false,
   val annotations: List<Annotation> = listOf()
) {
   /**
    * Returns a copy of this field, with any type
    * proxies resolved against real types from the typesystem.
    */
   fun resolveProxies(typeSystem: TypeSystem): Field {
      return when (this.type) {
         is TypeProxy -> this.copy(type = typeSystem.getType(this.name))
         is GenericType -> this.copy(type = type.resolveTypes(typeSystem))
         else -> this
      }
   }
}

