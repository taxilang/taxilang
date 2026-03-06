package lang.taxi.functions.vyne.aggregations

import lang.taxi.functions.stdlib.FunctionApi
import lang.taxi.types.QualifiedName

object Aggregations {
   val functions: List<FunctionApi> = listOf(
      SumOver
   )
   const val namespace = "vyne.aggregations"
   fun aggregationLibName(name:String): QualifiedName = QualifiedName.from("$namespace.$name")
}

object SumOver: FunctionApi {
   override val taxi: String = """
      [[
      A Vyne-specific aggregate function used in analytical queries to compute a running or grouped sum.

      Used in `sumOver` window-style queries where values are aggregated across a result set.

      ```taxi
      find { orders: Order[] } as {
         totalRevenue: Decimal = sumOver(Amount)
      }
      ```
      ]]
      declare query function sumOver(Any...):Decimal""".trimIndent()
   override val name: QualifiedName = Aggregations.aggregationLibName("sumOver")
}
