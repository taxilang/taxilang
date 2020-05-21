package lang.taxi.lsp.formatter

import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceFor
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Position
import org.junit.Assert.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class FormatterServiceTest : Spek({
    describe("generating formatting changes") {
        it("should generate formatting edits correctly") {
            val (service, workspaceRoot) = documentServiceFor("test-scenarios/unformatted")
            val edits = service.formatting(DocumentFormattingParams(
                    workspaceRoot.document("trade.taxi"),
                    FormattingOptions(3, true)
            )).get()
            // TODO ... test this stuff
        }
    }

})