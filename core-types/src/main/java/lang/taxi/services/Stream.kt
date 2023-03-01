package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.Documented
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

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
) : ServiceMember, Annotatable, Compiled, Documented, TaxiStatementGenerator {

   override val parameters: List<Parameter> = emptyList()

   private val equality = ImmutableEquality(
      this,
      Stream::name,
      Stream::annotations,
      Stream::returnType,
   )

   override fun asTaxi(): String {
      val annotations = this.annotations.joinToString { it.asTaxi() }
      return """$annotations stream
         |${this.name} : ${this.returnType.toQualifiedName().parameterizedName}"""
         .trimMargin()
   }

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}
