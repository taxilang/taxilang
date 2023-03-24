package lang.taxi.query

import lang.taxi.accessors.Argument
import lang.taxi.types.Annotatable
import lang.taxi.types.Annotation
import lang.taxi.types.Type

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

   override val type: Type
      get() = value.type
}
