package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object EnumFunctions {
   val functions: List<FunctionApi> = listOf(
      EnumForName,
      HasEnumNamed
      )
}

object EnumForName : FunctionApi {
   override val taxi: String = """
[[ Returns the enum value for the provided name ]]
declare extension function <T> enumForName(enumType: lang.taxi.Type<T>, enumName: String): T
   """.trimIndent()
   override val name: QualifiedName = stdLibName("enumForName")

}

object HasEnumNamed : FunctionApi {
   override val taxi: String = """
[[ Indicates if the enum contains an entry for the provided key.

Ignores default entries, so an enum with a default will return false to enumContains()
 if the key isn't explicitly defined.
 ]]
declare extension function <T> hasEnumNamed(enumType: lang.taxi.Type<T>, enumName: String): Boolean
   """.trimIndent()
   override val name: QualifiedName = stdLibName("hasEnumNamed")

}
