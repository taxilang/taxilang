package lang.taxi.functions

import lang.taxi.Equality
import lang.taxi.services.Parameter
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.DefinableToken
import lang.taxi.types.ImportableToken
import lang.taxi.types.Named
import lang.taxi.types.TokenDefinition
import lang.taxi.types.Type

class FunctionDefinition(val parameters: List<Parameter>,
                         val returnType: Type,
                         override val compilationUnit: CompilationUnit) : TokenDefinition {
   private val equality = Equality(this, FunctionDefinition::parameters, FunctionDefinition::returnType)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

class Function(override val qualifiedName: String, override var definition: FunctionDefinition?) : Named, Compiled, ImportableToken, DefinableToken<FunctionDefinition> {
   fun getParameterType(parameterIndex: Int): Type {
      return when {
          parameterIndex < this.parameters.size -> {
             this.parameters[parameterIndex].type
          }
          this.parameters.last().isVarArg -> {
             return this.parameters.last().type
          }
          else -> {
             error("Parameter index $parameterIndex is out of bounds - function $qualifiedName only takes ${this.parameters.size} parameters")
          }
      }
   }

   override val compilationUnits: List<CompilationUnit> = listOfNotNull(definition?.compilationUnit)

   companion object {
      fun undefined(name: String): Function {
         return Function(name, definition = null)
      }
   }

   val parameters: List<Parameter>
      get() {
         return if (isDefined) this.definition!!.parameters else emptyList()
      }

   val returnType: Type?
      get() {
         return if (isDefined) this.definition!!.returnType else null
      }

}
