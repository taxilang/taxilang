package lang.taxi.lsp

import lang.taxi.CompilationError
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.lsp.completion.normalizedUriPath
import lang.taxi.messages.Severity
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.time.Duration

/**
 * Stores the compiled snapshot for a file
 * Contains both the TaxiDocument - for accessing types, etc,
 * and the compiler, for accessing tokens and compiler context - useful
 * for completion
 */
data class CompilationResult(
   val compiler: Compiler,
   val document: TaxiDocument?,
   override val countOfSources: Int,
   override val duration: Duration,
   val errors: List<CompilationError> = emptyList()
) : DiagnosticMessagesWrapper {
   val documentOrEmpty: TaxiDocument = document ?: TaxiDocument.empty()

   override val messages: Map<String, List<Diagnostic>>
      get() {
         return convertCompilerMessagesToDiagnotics(this.errors)
      }


   fun containsTokensForSource(uri: String): Boolean {
      return compiler.containsTokensForSource(uri)
   }

   fun getNearestToken(textDocument: TextDocumentIdentifier, position: Position): ParserRuleContext? {
      val normalizedUriPath = textDocument.normalizedUriPath()
      val zeroBasedLineIndex = position.line
      val char = position.character
      // contextAt() is the most specific.  If we match an exact token, it'll be returned.
      return compiler.contextAt(zeroBasedLineIndex, char, normalizedUriPath)
         ?:
         // getNearestToken() returns the token if we're not on an exact match location, but could find a nearby one.
         compiler.getNearestToken(
            zeroBasedLineIndex,
            char,
            normalizedUriPath
         ) as? ParserRuleContext
   }

   fun getSource(textDocument: TextDocumentIdentifier): CharStream? {
      return compiler.inputs.firstOrNull {
         it.sourceName == textDocument.normalizedUriPath()
      }
   }

   val successful = document != null && errors.none { it.severity == Severity.ERROR }

   private fun convertCompilerMessagesToDiagnotics(compilerMessages: List<CompilationError>): Map<String, List<Diagnostic>> {
      val diagnostics = compilerMessages.map { error ->
         // Note - for VSCode, we can use the same position for start and end, and it
         // highlights the entire word
         val position = Position(
            error.line - 1,
            error.char
         )
         val severity: DiagnosticSeverity = when (error.severity) {
            Severity.INFO -> DiagnosticSeverity.Information
            Severity.WARNING -> DiagnosticSeverity.Warning
            Severity.ERROR -> DiagnosticSeverity.Error
         }
         (error.sourceName ?: UnknownSource.UNKNOWN_SOURCE) to Diagnostic(
            Range(position, position),
            error.detailMessage,
            severity,
            "Compiler"
         )
      }
      return diagnostics.groupBy({ it.first }, { it.second })
         .mapKeys { (fileUri, _) -> SourceNames.normalize(fileUri) }
   }

}
