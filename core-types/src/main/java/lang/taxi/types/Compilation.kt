package lang.taxi.types

import lang.taxi.sources.SourceCode
import lang.taxi.sources.SourceLocation

typealias ErrorMessage = String

interface Compiled {
   val compilationUnits: List<CompilationUnit>
}

/**
 * A compilation Unit is provided during compilation time.
 * The ruleContext is provided by an underlying compiler (eg., from Antlr).
 * It's typed as Any? here, because we want to avoid pulling in the whole
 * Antlr and parser tree dependencies.
 */
data class CompilationUnit(
   val source: SourceCode,
   val location: SourceLocation = SourceLocation.UNKNOWN_POSITION
) {
   companion object {
      fun unspecified(): CompilationUnit {
         return CompilationUnit(source = SourceCode.unspecified())
      }

      fun ofSource(source: SourceCode): CompilationUnit {
         return CompilationUnit(source)
      }

      fun generatedFor(type: Type): CompilationUnit {
         return CompilationUnit(SourceCode("Generated for ${type.qualifiedName}", ""))
      }
      fun generatedFor(name: String): CompilationUnit {
         return CompilationUnit(SourceCode("Generated for $name", ""))
      }
   }
}
