package lang.taxi.functions.vyne.aggregations

import lang.taxi.functions.stdlib.FunctionApi
import lang.taxi.types.QualifiedName

object Aggregations {
   val functions: List<FunctionApi> = listOf(
      SumOver
   )
   val namespace = "vyne.aggregations"
   fun aggregationLibName(name:String): QualifiedName = QualifiedName.from("$namespace.$name")
}

object SumOver: FunctionApi {
   override val taxi: String = "declare query function sumOver(Any...):Decimal"
   override val name: QualifiedName = Aggregations.aggregationLibName("sumOver")
}
