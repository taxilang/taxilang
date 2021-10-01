package lang.taxi.functions

import lang.taxi.ImmutableEquality
import lang.taxi.accessors.Accessor
import lang.taxi.generics.TypeArgumentResolver
import lang.taxi.services.Parameter
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.DefinableToken
import lang.taxi.types.ImportableToken
import lang.taxi.types.Named
import lang.taxi.types.TokenDefinition
import lang.taxi.types.Type
import lang.taxi.types.TypeArgument
import java.util.EnumSet


enum class FunctionModifiers {
   Query
}

class FunctionDefinition(
   val parameters: List<Parameter>,
   val returnType: Type,
   val modifiers: EnumSet<FunctionModifiers>,
   val typeArguments: List<TypeArgument>,
   override val compilationUnit: CompilationUnit
) : TokenDefinition {
   private val equality = ImmutableEquality(this, FunctionDefinition::parameters, FunctionDefinition::returnType)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
   fun resolveTypeParametersFromInputs(inputs: List<Accessor>): FunctionDefinition {
      if (this.typeArguments.isEmpty()) {
         return this
      }
      val resolvedTypeArguments = TypeArgumentResolver.resolve(typeArguments, parameters.map { it.type }, inputs)
      val resolvedParameters = TypeArgumentResolver.replaceTypeArguments(parameters, resolvedTypeArguments)
      val resolvedReturnType = TypeArgumentResolver.replaceType(returnType, resolvedTypeArguments)
      return FunctionDefinition(
         resolvedParameters,
         resolvedReturnType,
         modifiers, typeArguments, compilationUnit
      )
   }
}

data class Function(
   override val qualifiedName: String,
   override var definition: FunctionDefinition?
) : Named, Compiled, ImportableToken, DefinableToken<FunctionDefinition> {
   val typeArguments:List<TypeArgument>? = definition?.typeArguments
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

   fun resolveTypeParametersFromInputs(inputs: List<Accessor>): FunctionDefinition {
      require(definition != null) { "Function $qualifiedName must be defined before resolveGenericsFromInputs can be called" }
      return definition!!.resolveTypeParametersFromInputs(inputs)
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

   val modifiers: EnumSet<FunctionModifiers>
      get() {
         return if (isDefined) this.definition!!.modifiers else EnumSet.noneOf(FunctionModifiers::class.java)
      }

}
