package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.types.*
import lang.taxi.types.Annotation

/**
 * A stream of messages from a message broker (or similar).
 * Eg: A topic, or queue.
 * Use annotations and metadata to further specify behaviour
 */
class Stream(
   override val name: String,
   override val annotations: List<Annotation>,
   override val returnType: Type,
   override val compilationUnits: List<CompilationUnit>,
   override val typeDoc: String? = null
) : ServiceMember, Annotatable, Compiled, Documented {

   override val parameters: List<Parameter> = emptyList()

   private val equality = ImmutableEquality(
      this,
      Stream::name,
      Stream::annotations,
      Stream::returnType,
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}
