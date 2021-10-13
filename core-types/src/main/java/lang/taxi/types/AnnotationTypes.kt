package lang.taxi.types

import arrow.core.Either
import lang.taxi.ImmutableEquality

data class AnnotationTypeDefinition(
   val fields: List<Field> = emptyList(),
   override val annotations: List<Annotation> = emptyList(),
   override val typeDoc: String? = null,
   override val compilationUnit: CompilationUnit
) : Annotatable, TypeDefinition, Documented {
   private val equality = ImmutableEquality(
      this,
      AnnotationTypeDefinition::fields,
      AnnotationTypeDefinition::annotations,
      AnnotationTypeDefinition::typeDoc
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

data class AnnotationType(
   override val qualifiedName: String,
   override var definition: AnnotationTypeDefinition?
) : UserType<AnnotationTypeDefinition, Nothing>, Annotatable, Documented, TaxiStatementGenerator {
   companion object {
      fun undefined(name: String): AnnotationType {
         return AnnotationType(name, definition = null)
      }
   }

   private val wrapper = LazyLoadingWrapper(this)
   override val extensions: List<Nothing> = emptyList()
   override fun addExtension(extension: Nothing): Either<ErrorMessage, Nothing> {
      error("Extensions on annotations are not supported")
   }

   fun field(name: String): Field {
      return fields.firstOrNull { it.name == name }
         ?: error("Annotation $qualifiedName does not have a field name $name")
   }

   override val annotations: List<Annotation>
      get() {
         return definition?.annotations ?: emptyList()
      }

   override val typeDoc: String?
      get() {
         return definition?.typeDoc
      }

   val fields: List<Field>
      get() {
         return definition?.fields ?: emptyList()
      }

   override val inheritsFrom: Set<Type> = emptySet()
   override val allInheritedTypes: Set<Type> = emptySet()
   override val format: List<String> = emptyList()
   override val inheritsFromPrimitive: Boolean = false
   override val basePrimitive: PrimitiveType? = null
   override val formattedInstanceOfType: Type? = null
   override val definitionHash: String?
      get() {
         return if (isDefined) wrapper.definitionHash else null
      }
//   formulas replaced by expressions
//   override val calculation: Formula? = null
   override val referencedTypes: List<Type> = emptyList()
   override val offset: Int? = null
   override fun asTaxi(): String {
      val annotationTaxi = this.annotations.joinToString("\n") { it.asTaxi() }
      val fieldTaxi = this.fields.joinToString("\n") { field ->
         val fieldAnnotations = field.annotations.joinToString("\n") { it.asTaxi() }
         val nullableMarker = if (field.nullable) {
            "?"
         } else ""
         """$fieldAnnotations
${field.name} : ${field.type.qualifiedName}$nullableMarker""".trim()
      }
      return """
$annotationTaxi
annotation ${this.toQualifiedName().typeName} {
   $fieldTaxi
}
""".trim()
   }

   override val typeKind: TypeKind = TypeKind.Type
}
