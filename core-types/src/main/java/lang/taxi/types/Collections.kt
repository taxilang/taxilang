package lang.taxi.types

object Collections {
   /**
    * If the provided type is an array or a stream, returns the inner member type.
    */
   fun memberTypeOrType(type: Type):Type {
      return when (type) {
         is ArrayType -> type.memberType
         is StreamType -> type.type
         else -> type
      }
   }
}
