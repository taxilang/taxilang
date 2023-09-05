package lang.taxi.query

import lang.taxi.accessors.Argument
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.Type
import lang.taxi.types.TypedValue

data class Parameter(
   override val name: String,
   val value: FactValue,
   override val annotations: List<Annotation>
) : Argument, Annotatable {
   companion object {
      fun variable(name: String, type: Type, annotations: List<Annotation> = emptyList()): Parameter {
         return Parameter(name, FactValue.Variable(type, name), annotations)
      }
   }

   /**
    * Returns the underlying value.
    * If this parameter contains a contsant value,
    * then it is returned.
    *
    * Otherwise, attempts to resolve via the named arguments,
    * matching on name.
    */
   fun resolveValue(arguments: Map<String, Any?>): TypedValue {
      // Arguments are Map<String,Any?> when resolving on an inbound
      // TaxiQL query.
      // TODO : Also consider accepting Scopes here? Seems sensible
      // TODO: Can we run some type checking here too?
      return if (value is FactValue.Constant) {
         value.value
      } else {
         if (arguments.containsKey(name)) {
            val argumentValue = arguments[name]
            TypedValue(value.type, argumentValue)
         } else {
            error("Unable to resolve value of parameter $name - no constant is defined, and no matching arguments were provided")
         }
      }
   }

   override val type: Type
      get() = value.type
}
