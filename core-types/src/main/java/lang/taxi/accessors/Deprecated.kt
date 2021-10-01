package lang.taxi.accessors

@Deprecated("Destructuring is being removed")
data class DestructuredAccessor(val fields: Map<String, Accessor>) : Accessor


@Deprecated("Use lang.taxi.functions.Function instead")
data class ReadFunctionFieldAccessor(val readFunction: ReadFunction, val arguments: List<ReadFunctionArgument>) :
   Accessor

@Deprecated("Use lang.taxi.functions.Function instead")
data class ReadFunctionArgument(val columnAccessor: ColumnAccessor?, val value: Any?)

@Deprecated("Use lang.taxi.functions.Function instead")
enum class ReadFunction(val symbol: String) {
   CONCAT("concat");

   //   LEFTUPPERCASE("leftAndUpperCase"),
//   MIDUPPERCASE("midAndUpperCase");
   companion object {
      private val bySymbol = ReadFunction.values().associateBy { it.symbol }
      fun forSymbol(symbol: String): ReadFunction {
         return bySymbol[symbol] ?: error("No operator defined for symbol $symbol")
      }

      fun forSymbolOrNull(symbol: String) = bySymbol[symbol]
   }
}
