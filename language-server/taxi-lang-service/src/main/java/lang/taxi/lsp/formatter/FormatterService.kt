package lang.taxi.lsp.formatter

import lang.taxi.formatter.TaxiCodeFormatter
import lang.taxi.formatter.TaxiFormattingOptions
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import java.util.concurrent.CompletableFuture

class FormatterService {
   fun getChanges(source: String, options: FormattingOptions): CompletableFuture<MutableList<out TextEdit>> {

      val taxiFormattingOptions = TaxiFormattingOptions(options.tabSize, options.isInsertSpaces)
      val sourceLines = source.lines()
      val formattedLines = TaxiCodeFormatter.format(source, taxiFormattingOptions)
      val lastPosition = Position(
         (sourceLines.size - 1).coerceAtLeast(0),
         (sourceLines.last().length)
      )

      val edit = TextEdit(Range(Position(0, 0), lastPosition), formattedLines)
      return CompletableFuture.completedFuture(mutableListOf(edit))
   }
}
