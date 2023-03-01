package lang.taxi.services

import lang.taxi.ImmutableEquality
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.Documented
import lang.taxi.types.Type

data class Operation(
   override val name: String,
   val scope: String? = null,
   override val annotations: List<Annotation>,
   override val parameters: List<Parameter>,
   override val returnType: Type,
   override val compilationUnits: List<CompilationUnit>,
   val contract: OperationContract? = null,
   override val typeDoc: String? = null
) : ServiceMember, Annotatable, Compiled, Documented {
   private val equality = ImmutableEquality(
      this,
      Operation::name,
      Operation::annotations,
      Operation::parameters,
      Operation::returnType,
      Operation::contract
   )

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

