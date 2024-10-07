package lang.taxi.functions.stdlib

import lang.taxi.types.QualifiedName

object EnumFunctions {
   val functions: List<FunctionApi> = listOf(EnumForName)
}

object EnumForName : FunctionApi {
   override val taxi: String = """
[[ Returns the enum value for the provided name ]]
declare extension function <T> enumForName(enumType: lang.taxi.Type<T>, enumName: String): T
   """.trimIndent()
   override val name: QualifiedName = stdLibName("enumForName")

}
