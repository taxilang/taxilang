package lang.taxi.sources

import lang.taxi.types.SourceNames
import java.io.File
import java.nio.file.Path

data class SourceLocation(val line: Int, val char: Int) {
   companion object {
      val UNKNOWN_POSITION = SourceLocation(1, 1)
   }
}

data class SourceCode(
   val sourceName: String,
   val content: String,
   var path: Path? = null
) {
   companion object {
      fun unspecified(): SourceCode = SourceCode("Not specified", "")
      fun from(file: File): SourceCode {
         return from(file.toPath())
      }

      fun from(path: Path): SourceCode {
         return SourceCode(SourceNames.normalize(path.toUri()), path.toFile().readText(), path)
      }
   }

   val normalizedSourceName: String = lang.taxi.types.SourceNames.normalize(sourceName)
}

