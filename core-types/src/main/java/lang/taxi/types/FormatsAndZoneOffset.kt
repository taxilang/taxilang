package lang.taxi.types


interface Formattable {
   val format: List<String>?

   /**
    * Indicates if this type (excluding any inherited types)
    * declares a format.
    *
    * Note that the format property will return formats
    * from inherited types.
    */
   val declaresFormat: Boolean
      get() {
         return this.format?.isNotEmpty() ?: false
      }

   val offset: Int?

   val formatAndZoneOffset: FormatsAndZoneOffset?

}
data class FormatsAndZoneOffset(val patterns: List<String>, val utcZoneOffsetInMinutes: Int?) {
   val definesPattern = patterns.isNotEmpty()
   companion object {
      fun forNullable(formats:List<String>?, offset: Int?):FormatsAndZoneOffset? {
         return if (formats == null && offset == null) {
            null
         } else {
            FormatsAndZoneOffset(formats ?: emptyList(), offset)
         }

      }
      fun forFormat(vararg format:String):FormatsAndZoneOffset = FormatsAndZoneOffset(format.toList(), null)
      fun empty() = FormatsAndZoneOffset(emptyList(), null)
   }
}
