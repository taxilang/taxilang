package lang.taxi.types

import lang.taxi.sources.SourceCode
import lang.taxi.sources.SourceLocation
import java.lang.Exception
import java.net.URI

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
data class CompilationUnit(val ruleContext: Any?,
                           val source: SourceCode,
                           val location: SourceLocation = SourceLocation.UNKNOWN_POSITION) {
   companion object {
      fun unspecified(): CompilationUnit {
         return CompilationUnit(ruleContext = null, source = SourceCode.unspecified())
      }

      fun ofSource(source: SourceCode): CompilationUnit {
         return CompilationUnit(null, source)
      }

//        fun <T> of(typeRule: T): CompilationUnit<T> {
//            return CompilationUnit(typeRule, typeRule.source())
//        }
   }
}
