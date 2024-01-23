package lang.taxi.functions

import lang.taxi.ImmutableEquality
import lang.taxi.accessors.Accessor
import lang.taxi.generics.TypeArgumentResolver
import lang.taxi.generics.TypeResolutionFailedException
import lang.taxi.services.Parameter
import lang.taxi.types.*
import java.util.EnumSet


enum class FunctionModifiers {
   Query
}

class FunctionDefinition(
   val parameters: List<Parameter>,
   val returnType: Type,
   val modifiers: EnumSet<FunctionModifiers>,
   val typeArguments: List<TypeArgument>,
   override val typeDoc: String? = null,
   override val compilationUnit: CompilationUnit
) : TokenDefinition, Documented {
   private val equality = ImmutableEquality(this, FunctionDefinition::parameters, FunctionDefinition::returnType)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
   fun resolveTypeParameters(
      inputs: List<Accessor>,
      assignmentType: Type,
      /**
       * Throws an exception if there are unresolved parameters.
       * opt-in, as this is a new behaviour, but you should enable it.
       */
      requireAllParametersResolved: Boolean = false,
      functionName: String
   ): FunctionDefinition {
      if (this.typeArguments.isEmpty()) {
         return this
      }

      // Check: Is the return type of the function a type argument?
      // eg: declare function <T> foo():T
      // Also, is the assignment type declared? (ie., not ANY)
      // If so, we can use the assignment type to resolve the
      // return type, which is stricter (and more explicit) than allowing the
      // input params to infer the return type
      val resolveReturnTypeFromAssignment = typeArguments.contains(returnType)
         && parameters.none { TypeArgumentResolver.declarationCanResolveArgument(it.type, returnType) }
         && assignmentType != PrimitiveType.ANY
      val (resolvedReturnTypeArgument, typeArgumentsToResolveFromInputs) = if (resolveReturnTypeFromAssignment) {
         mapOf(returnType as TypeArgument to assignmentType) to typeArguments.filter { it != returnType }
      } else (emptyMap<TypeArgument, Type>() to typeArguments)


      val resolvedParameterTypeArguments =
         TypeArgumentResolver.resolve(typeArgumentsToResolveFromInputs, parameters.map { it.type }, inputs)
      val allResolvedTypeArguments = resolvedReturnTypeArgument + resolvedParameterTypeArguments
      if (requireAllParametersResolved) {
         val errors = allResolvedTypeArguments.values
            .filterIsInstance<TypeArgument>()
            .map { "Insufficient information to resolve type argument ${it.declaredName} in function $functionName" }

         // TODO : There's probably other possibilities for unresolved type expressions.
         // Enrich and test and we go.
         if (errors.isNotEmpty()) {
            throw TypeResolutionFailedException(errors)
         }
      }


      val resolvedParameters = TypeArgumentResolver.replaceTypeArguments(parameters, allResolvedTypeArguments)

      val resolvedReturnType = TypeArgumentResolver.replaceType(returnType, allResolvedTypeArguments)
      return FunctionDefinition(
         resolvedParameters,
         resolvedReturnType,
         modifiers, typeArguments, typeDoc, compilationUnit
      )
   }
}

data class Function(
   override val qualifiedName: String,
   override var definition: FunctionDefinition?
) : Named, Compiled, ImportableToken, DefinableToken<FunctionDefinition>, Documented {
   val typeArguments: List<TypeArgument>? = definition?.typeArguments
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

   fun resolveTypeParametersFromInputs(
      inputs: List<Accessor>, targetType: Type,
      /**
       * Throws an exception if there are unresolved parameters.
       * opt-in, as this is a new behaviour, but you should enable it.
       */
      requireAllParametersResolved: Boolean = false
   ): FunctionDefinition {
      require(definition != null) { "Function $qualifiedName must be defined before resolveGenericsFromInputs can be called" }
      return definition!!.resolveTypeParameters(inputs, targetType, requireAllParametersResolved, this.qualifiedName)
   }

   override val compilationUnits: List<CompilationUnit> = listOfNotNull(definition?.compilationUnit)

   companion object {
      fun undefined(name: String): Function {
         return Function(name, definition = null)
      }
   }

   override val typeDoc: String?
      get() {
         return if (isDefined) this.definition!!.typeDoc else null;
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
