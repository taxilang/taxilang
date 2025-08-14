package lang.taxi.types

import arrow.core.Either
import lang.taxi.ImmutableEquality

data class AnnotationTypeDefinition(
   val fields: List<Field> = emptyList(),
   override val annotations: List<Annotation> = emptyList(),
   val inheritsFrom: List<Type> = emptyList(),
   override val typeDoc: String? = null,
   override val compilationUnit: CompilationUnit
) : Annotatable, TypeDefinition, Documented {
   private val equality = ImmutableEquality(
      this,
      AnnotationTypeDefinition::fields,
      AnnotationTypeDefinition::annotations,
      AnnotationTypeDefinition::inheritsFrom,
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

   fun fieldOrNull(name: String): Field? {
      return allFields.firstOrNull { it.name == name }
   }
   fun field(name: String): Field {
      return fieldOrNull(name)
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

   val inheritedFields: List<Field>
      get() {
         return allInheritedTypes
            .filterIsInstance<AnnotationType>()
            .flatMap { it.allFields }
      }

   private val allFieldsMap:Map<String, Field>
      get() {
         return Field.mergeInheritedFields(fields, inheritedFields)
      }

   val allFields: List<Field>
      get() {
         return allFieldsMap.values.toList()
      }

   override val formatAndZoneOffset: FormatsAndZoneOffset? = null
   override val inheritsFrom: List<Type>
      get() {
         return definition?.inheritsFrom ?: emptyList()
      }
   override val allInheritedTypes: Set<Type>
      get() {
         return if (isDefined) wrapper.allInheritedTypes else emptySet()
      }
   override val format: List<String> = emptyList()
   override val inheritsFromPrimitive: Boolean = false
   override val basePrimitive: PrimitiveType? = null
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
      val inheritsClause = if (inheritsFrom.isNotEmpty()) {
         " inherits " + inheritsFrom.joinToString(", ") { it.qualifiedName }
      } else ""

      return """
$annotationTaxi
annotation ${this.toQualifiedName().typeName}$inheritsClause {
   $fieldTaxi
}
""".trim()
   }

   override val typeKind: TypeKind = TypeKind.Type
}
