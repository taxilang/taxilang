package lang.taxi.lsp.formatter

import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceFor
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions

class FormatterServiceTest : DescribeSpec({
   describe("generating formatting changes") {
      it("should generate formatting edits correctly") {
         val (service, workspaceRoot) = documentServiceFor("test-scenarios/unformatted")
         val edits = service.formatting(
            DocumentFormattingParams(
               workspaceRoot.document("trade.taxi"),
               FormattingOptions(3, true)
            )
         ).get()
         // TODO ... test this stuff
      }
   }

})
