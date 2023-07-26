package lang.taxi.query

import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.types.QualifiedName
import lang.taxi.types.Type
import lang.taxi.types.TypedValue

sealed class FactValue() {
   abstract val type:Type
   data class Constant(val value: TypedValue) : FactValue() {
      override val type: Type = value.type
   }
   data class Variable(override val type: Type, val name: String) : FactValue()

   /**
    * Convenience method
    */
   val typedValue: TypedValue
      get() {
         return when (this) {
            is Constant -> this.value
            else -> error("This variable does not contain a constant")
         }
      }

   val hasValue: Boolean
      get() {
         return this is Constant
      }
   val variableName: String
      get() {
         return when (this) {
            is Variable -> this.name
            else -> error("This variable is constant and does not have a variable name")
         }
      }
}

data class DiscoveryType(
   val type: Type,
   val constraints: List<Constraint>,
   /**
    * Starting facts aren't the same as a constraint, in that they don't
    * constraint the output type.  However, they do inform query strategies,
    * so we pop them here for query operations to consider.
    */
   val startingFacts: List<Parameter>,
   /**
    * If the query body is an anonymoust type store the definition here,
    */
   val anonymousType: Type? = null
) {
   val typeName: QualifiedName = type.toQualifiedName()
}


enum class QueryMode(val directive: String) {
   @Deprecated("FIND_ONE is no longer supported")
   FIND_ONE("findOne"),
   FIND_ALL("find"),
   // See the grammar for thoughts around this.
   MAP("map"),
   STREAM("stream"),
   MUTATE("call");

   companion object {
      fun forToken(token: String): QueryMode {
         // Legacy support - findOne and findAll are deprcated in favour of find
         return if (token == "find") {
            FIND_ALL
         } else {
            values().first { it.directive == token }
         }
      }
   }
}

typealias TaxiQLQueryString = String
