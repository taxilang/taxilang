package lang.taxi.accessors

import arrow.core.Either
import arrow.core.right
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type

interface Accessor {
   fun enabledForValueType(value: Any): Boolean {
      return true
   }

   val returnType: Type
      get() = PrimitiveType.ANY

   /**
    * Allows types to provide a returnType that's stricter
    * for compilation pruposes. Specifically, allows operationTypes
    * to indicate an inferred returntype (by looking at it's inputs and guessing),
    * but defaulting to Any, then defaulting to null when it can't tell
    */
   val strictReturnType: Either<String, Type>
      get() = returnType.right()
}

interface PathBasedAccessor : Accessor {
   @Deprecated("use Path", replaceWith = ReplaceWith("path"))
   val expression: String
      get() = path

   val path: String
}
