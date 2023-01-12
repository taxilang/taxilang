package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.Documented
import lang.taxi.types.TaxiStatementGenerator
import lang.taxi.types.Type

data class Table(
   override val name: String,
   override val annotations: List<Annotation>,
   override val returnType: Type,
   override val compilationUnits: List<CompilationUnit>,
   override val typeDoc: String? = null
) : ServiceMember, Annotatable, Compiled, Documented, TaxiStatementGenerator {

   override val parameters: List<Parameter> = emptyList()

   private val equality = ImmutableEquality(
      this,
      Table::name,
      Table::annotations,
      Table::returnType,
   )

   override fun asTaxi(): String {
      val annotations = this.annotations.joinToString { it.asTaxi() }
      return """$annotations table
         |${this.name} : ${this.returnType.toQualifiedName().parameterizedName}"""
         .trimMargin()
   }

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}
