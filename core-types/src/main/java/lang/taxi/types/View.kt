package lang.taxi.types

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import lang.taxi.Equality

data class ViewDefinition(
   val inheritsFrom: Set<Type>,
   val annotations: Set<Annotation> = emptySet(),
   val modifiers: List<Modifier> = emptyList(),
   val viewBodyDefinitions: List<ViewBodyDefinition> = emptyList(),
   override val typeDoc: String? = null,
   override val compilationUnit: CompilationUnit): TokenDefinition, Documented {
   private val equality = Equality(this, ViewDefinition::annotations.toSet(), ViewDefinition::modifiers.toSet())
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

data class JoinInfo(val mainField: Field, val joinField: Field)
data class ViewBodyDefinition(
   val bodyType: Type,
   val joinType: Type? = null,
   val viewBodyTypeDefinition: ViewBodyTypeDefinition? = null,
   val joinInfo: JoinInfo? = null)
data class ViewBodyTypeDefinition(val fields: List<ViewBodyFieldDefinition> = emptyList())
data class ViewBodyFieldDefinition(val sourceType: Type, val fieldType: Type, val fieldName: String, val accessor: ConditionalAccessor? = null)

/*
  Representation of a view:
  view View inherits Order with query {
      find { Broker1Order[]( joinTo Broker1Trade[]) }
   }
 */
class View(
   override val qualifiedName: String,
   override var definition: ViewDefinition?) : DefinableToken<ViewDefinition>, Annotatable, Documented {
   override val typeDoc: String?
      get() {
        return definition?.typeDoc
      }
   override val annotations: List<lang.taxi.types.Annotation>
      get() {
        return definition?.annotations?.toList()?: listOf()
      }

   override val compilationUnits: List<CompilationUnit>
      get() = this.definition?.compilationUnit?.let { listOf(it) } ?: listOf()

   val inheritsFrom: Set<Type>
      get() {
         return definition?.inheritsFrom ?: emptySet()
      }

   val viewBodyDefinitions: List<ViewBodyDefinition>?
     get() {
        return definition?.viewBodyDefinitions
     }

   val definitionHash: String by lazy {
      val hasher = Hashing.sha256().newHasher()
      computeTypeHash(hasher)
      hasher.hash().toString().substring(0, 6)
   }

   private fun computeTypeHash(hasher: Hasher) {
      this.compilationUnits
         .sortedBy { it.source.content.hashCode() }
         .forEach {
            hasher.putUnencodedChars(it.source.content)
         }

      this.viewBodyDefinitions?.forEach { viewBodyDefinition ->
         viewBodyDefinition.bodyType.definitionHash?.let { it ->
            hasher.putUnencodedChars(it)
         }

         viewBodyDefinition.joinType?.let {joinType ->
            joinType.definitionHash?.let { hasher.putUnencodedChars(it) }
         }
      }
   }


   companion object {
      const val JoinAnnotationName = "Id"
   }
}
