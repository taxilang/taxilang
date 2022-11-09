package lang.taxi.types

data class FormatsAndZoneOffset(val formats: List<String>, val utcZoneoffsetInMinutes: Int?) {
   companion object {
      fun empty() = FormatsAndZoneOffset(emptyList(), null)
   }
}
