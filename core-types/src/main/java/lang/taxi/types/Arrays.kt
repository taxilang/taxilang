package lang.taxi.types

object Arrays {
   /**
    * Returns a name of a type where the collection type is the provided member
    * eg:  for T returns the name of T[]
    */
   fun nameOfArray(memberType: QualifiedName): QualifiedName {
      return QualifiedName.from(ArrayType.NAME).copy(parameters = listOf(memberType))
   }

   fun isArray(qualifiedName: QualifiedName): Boolean {
      // Use fullyQualifiedName here, not ParamaterizedName
      return qualifiedName.fullyQualifiedName == ArrayType.NAME
   }

   fun isArray(parameterizedName:String):Boolean {
      return parameterizedName.startsWith(ArrayType.NAME)
   }
   fun isArray(type: Type): Boolean {
      return Arrays.isArray(type.toQualifiedName())
   }

   /**
    * Returns the member type of the array, if the provided type is an array,
    * otherwise just returns the provided type as-is
    */
   fun unwrapPossibleArrayType(type: Type): Type {
      return if (isArray(type)) {
         (type as ArrayType).type
      } else {
         type
      }
   }

   fun arrayOf(type: Type): Type {
      return ArrayType(type, CompilationUnit.unspecified())
   }
}
