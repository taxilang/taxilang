package lang.taxi.sources

data class SourceLocation(val line: Int, val char: Int) {
   companion object {
      val UNKNOWN_POSITION = SourceLocation(1, 1)
   }
}

data class SourceCode(
   val sourceName: String,
   val content: String
) {
   companion object {
      fun unspecified(): SourceCode = SourceCode("Not specified", "")
   }

   val normalizedSourceName: String = lang.taxi.types.SourceNames.normalize(sourceName)
}

