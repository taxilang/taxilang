package lang.taxi.lsp

import com.winterbe.expekt.should
import org.eclipse.lsp4j.*
import org.junit.Test
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Path

object CompletionSpec : Spek({

    describe("completions") {
        it("should offer top-level language items at the start of the line") {
            val (service, workspaceRoot) = documentServiceFor("test-scenarios/simple-workspace")

            val completions = service.completion(CompletionParams(
                    workspaceRoot.document("trade.taxi"),
                    Position(5, 0)
            )).get().left

            completions.shouldContainLabels("type", "enum", "type alias")
        }

        describe("for types") {
            val (service, workspaceRoot) = documentServiceFor("test-scenarios/simple-workspace")

            val originalSource = workspaceRoot.resolve("trade.taxi").toFile().readText()

            // let's edit:
            val updatedSource = """$originalSource
    |
    |type Client {
    |   name :
""".trimMargin()
            service.didChange(DidChangeTextDocumentParams(
                    workspaceRoot.versionedDocument("trade.taxi"),
                    listOf(TextDocumentContentChangeEvent(
                            updatedSource
                    ))
            ))

            val cursorPositionLine = updatedSource.lines().indexOfFirst { it.contains("name :") }
            val cursorPositionChar = updatedSource.lines()[cursorPositionLine].indexOf(":")
            val completions = service.completion(CompletionParams(
                    workspaceRoot.document("trade.taxi"),
                    Position(cursorPositionLine, cursorPositionChar)
            )).get().left

            it("should contain types from within the same file") {
                val expectedLabels = listOf(
                        "Trade", "Client" // Types in the current file
                )
                completions.shouldContainLabels(expectedLabels)
            }

            it("should contain types from within another file") {
                val expectedLabels = listOf(
                        "Trade", "Client", // Types in the current file
                        "CurrencySymbol" // Type in another file
                )

                completions.shouldContainLabels(expectedLabels)
            }

            it("should include imports for types defined in another file") {
                val completion = completions.first { it.label == "BaseCurrency" }
                completion.additionalTextEdits.should.have.size(1)
                val edit = completion.additionalTextEdits.first()
                edit.newText.should.equal("import acme.fx.BaseCurrency\n")
            }
            // Note : Might wanna change this to insert imports alphabetically
            // TODO
            xit("should insert imports at the end of existing imports") {
                val completion = completions.first { it.label == "BaseCurrency" }
                completion.additionalTextEdits.should.have.size(1)
                val edit = completion.additionalTextEdits.first()
                edit.range.start.line.should.equal(1) // 0-based
            }
            it("should not include imports for types already imported") {
                val completion = completions.first { it.label == "CurrencySymbol" }
                completion.additionalTextEdits.should.be.empty
            }
            it("should not include imports for types declared in the file") {
                val completion = completions.first { it.label == "Trade" }
                completion.additionalTextEdits.should.be.empty
            }
            it("should not include imports for primitives") {
                val completion = completions.first { it.label == "String" }
                completion.additionalTextEdits.should.be.`null`
            }
        }
    }
})

private fun List<CompletionItem>.shouldContainLabels(expectedLabels: List<String>) {
    expectedLabels.forEach { expectedLabel ->
        this.should.satisfy { it.any { completion -> completion.label == expectedLabel } }
    }
}

private fun List<CompletionItem>.shouldContainLabels(vararg expectedLabels: String) {
    return shouldContainLabels(expectedLabels.toList())
}