package lang.taxi.query

import lang.taxi.accessors.Argument
import lang.taxi.types.Type

data class Parameter(
   override val name: String,
   val value: FactValue
):Argument {
   companion object {
      fun variable(name: String, type: Type): Parameter {
         return Parameter(name, FactValue.Variable(type, name))
      }
   }

   override val type: Type
      get() = value.type
}
