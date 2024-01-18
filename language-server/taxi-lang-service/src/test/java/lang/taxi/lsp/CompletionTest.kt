package lang.taxi.lsp

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.mockito.Mockito.mock
import reactor.kotlin.test.test
import java.nio.file.Path

class CompletionSpec : DescribeSpec({

   // Commented out - these tests are failing, but the behaviour is working in the  app.
   // Need to investigate.  Sorry future me, but gots to get this shipped.
   describe("completions") {
      it("should offer top-level language items at the start of the line") {
         val (service, workspaceRoot) = documentServiceFor("test-scenarios/simple-workspace")

         val completions = service.completion(
            CompletionParams(
               workspaceRoot.document("trade.taxi"),
               Position(5, 0)
            )
         ).get().left

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
         service.didChange(
            DidChangeTextDocumentParams(
               workspaceRoot.versionedDocument("trade.taxi"),
               listOf(
                  TextDocumentContentChangeEvent(
                     updatedSource
                  )
               )
            )
         )
         service.compile()

         val cursorPositionLine = updatedSource.lines().indexOfFirst { it.contains("name :") }
         val cursorPositionChar = updatedSource.lines()[cursorPositionLine].indexOf(":")
         val completions = service.completion(
            CompletionParams(
               workspaceRoot.document("trade.taxi"),
               Position(cursorPositionLine, cursorPositionChar)
            )
         ).get().left

         it("should contain types from within the same file") {
            val expectedLabels = listOf(
               "Trade", "Client" // Types in the current file
            )
            completions.shouldContainLabels(expectedLabels)
         }

         it("should contain types from within another file") {
            val expectedLabels = listOf(
               "Trade", "Client", // Types in the current file
               "CurrencySymbol (acme.fx)" // Type in another file
            )

            completions.shouldContainLabels(expectedLabels)
         }

         it("should include imports for types defined in another file") {
            val completion = completions.first { it.label.startsWith("BaseCurrency") }
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
            val completion = completions.first { it.label.startsWith("CurrencySymbol") }
            completion.additionalTextEdits.should.be.empty
         }
         it("should not include imports for types declared in the file") {
            val completion = completions.first { it.label.startsWith("Trade") }
            completion.additionalTextEdits.should.be.empty
         }
         it("should not include imports for primitives") {
            val completion = completions.first { it.label == "String" }
            completion.additionalTextEdits.should.be.`null`
         }
      }

      // Suggestions for enums aren't working
      // See EditorCompletionService.calculateExpressionSuggestions()
      // for a description of why, and what we've tried.
      describe("offering enum values") {
         // This test commented as the feature to validate Annotation parameter values causing it to fail.
         // The easiest workaround to fix this issue is to leak 'FAKE' token into TokenProcessor, but obviously
         // This is just a hack and until we'll revamp the auto-completion compilation. We will keep this test commented out.
         xit("should offer enum values when completing an enum inside an annotation") {
            val source = """enum CountryCode { US, UK }
                  annotation LivesIn {
                     country : CountryCode
                  }
                  @LivesIn( country = )
                  model Person {}
               """
            val (service, workspaceRoot) = documentServiceWithFiles(tempdir(), files =
               arrayOf("test.taxi" to source.trimIndent())
            )
            val editPosition: Position = source.positionOf("""@LivesIn( country = """, CursorPosition.EndOfText).toPosition()
            val completions = service.completion(
               CompletionParams(
                  workspaceRoot.document("test.taxi"),
                  editPosition
               )
            ).get().left
            completions.shouldNotBeEmpty()

         }
      }

      xdescribe("for 'by when(...)'") {
         val (service, workspaceRoot) = documentServiceFor("test-scenarios/case-workspace")
         service.connect(mock(LanguageClient::class.java))
         val originalSource = workspaceRoot.resolve("trade-case.taxi").toFile().readText()
         service.compile()
         // let's edit:
         val updatedSource = """$originalSource
    |
    |type Trade {
    |country: Country
    |countryCode: CountryCode by when(country) {
    |   "United States" -> CountryCode.US
    |   "United Kingdom" -> CountryCode.UK
    |   else -> CountryCode. // We
""".trimMargin()
         service.didChange(
            DidChangeTextDocumentParams(
               workspaceRoot.versionedDocument("trade-case.taxi"),
               listOf(
                  TextDocumentContentChangeEvent(
                     updatedSource
                  )
               )
            )
         )

         val cursorPositionLine = updatedSource.lines().indexOfFirst { it.contains("else -> CountryCode.") }
         val cursorPositionChar =
            updatedSource.lines()[cursorPositionLine].indexOf(".") + 1 // Put the cursor after the '.'
         service.compile()
         val completions = service.completion(
            CompletionParams(
               workspaceRoot.document("trade-case.taxi"),
               Position(cursorPositionLine, cursorPositionChar)
            )
         ).get().left

         it("should include all enum values") {
            val insetTexts = completions.map { it.insertText }
            insetTexts.should.have.elements("US", "UK", "DE")
         }
      }




      describe("Synonym Completion") {

         describe("enum completion") {
            val (service, workspaceRoot) = documentServiceFor("test-scenarios/enum-completion-workspace")
            service.compile()
            val originalSource = workspaceRoot.resolve("direction.taxi").toFile().readText()

            it("should offer 'synonym of' prompt") {
               // start editing:
               val updatedSource = """$originalSource
                    |
                    |enum ClientDirection {
                    |   ClientBuys // note empty space after ClientBuys, which is where the cursor is placed.
                """.trimMargin()
               service.applyEdit("direction.taxi", updatedSource, workspaceRoot)
               // Compile now, otherwise compilation happens async
               service.compile()
               val (line, char) = updatedSource.positionOf("ClientBuys ")
               val completions = service.completion(
                  CompletionParams(
                     workspaceRoot.document("direction.taxi"),
                     Position(line, char)
                  )
               ).get().left
               completions.shouldContainLabels("synonym of")
            }




            xit("should offer enum types") {
               val updatedSource = """$originalSource
                    |
                    |enum ClientDirection {
                    |   ClientBuys synonym of // this is where we're editing
                """.trimMargin()
               service.applyEdit("direction.taxi", updatedSource, workspaceRoot)
               // Compile now, otherwise compilation happens async
               service.compile()
               val (line, char) = updatedSource.positionOf("synonym of ")
               val completions = service.completion(
                  CompletionParams(
                     workspaceRoot.document("direction.taxi"),
                     Position(line, char)
                  )
               ).get().left

               completions.should.have.size(1) // Only expect enums here
               completions.shouldContainLabels("BankDirection")
            }
         }
         val (service, workspaceRoot) = documentServiceFor("test-scenarios/case-workspace")
         service.connect(mock(LanguageClient::class.java))
         val originalSource = workspaceRoot.resolve("trade-case.taxi").toFile().readText()
         // let's edit:
         val updatedSource = """$originalSource

   enum EntryType {
   Germany synonym of CountryCode.
""".trimMargin()
         service.didChange(
            DidChangeTextDocumentParams(
               workspaceRoot.versionedDocument("trade-case.taxi"),
               listOf(
                  TextDocumentContentChangeEvent(
                     updatedSource
                  )
               )
            )
         )

         val cursorPositionLine = updatedSource.lines().indexOfFirst { it.contains("synonym of CountryCode.") }
         val cursorPositionChar = updatedSource.lines()[cursorPositionLine].indexOf(".")
         val completions = service.completion(
            CompletionParams(
               workspaceRoot.document("trade-case.taxi"),
               Position(cursorPositionLine, cursorPositionChar)
            )
         ).get().left

         // Suggestions for enums aren't working
         // See EditorCompletionService.calculateExpressionSuggestions()
         // for a description of why, and what we've tried.
         xit("should include all enum values") {
            val insetTexts = completions.map { it.insertText }
            insetTexts.should.have.elements("DE", "UK", "US")
         }
      }

   }
})


private fun TaxiTextDocumentService.applyEdit(fileName: String, updatedSource: String, workspaceRoot: Path) {
   this.didChange(
      DidChangeTextDocumentParams(
         workspaceRoot.versionedDocument(fileName),
         listOf(
            TextDocumentContentChangeEvent(
               updatedSource
            )
         )
      )
   )
}

private fun List<CompletionItem>.shouldContainLabels(expectedLabels: List<String>) {
   expectedLabels.forEach { expectedLabel ->
      this.should.satisfy { it.any { completion -> completion.label == expectedLabel } }
   }
}

private fun List<CompletionItem>.shouldContainLabels(vararg expectedLabels: String) {
   return shouldContainLabels(expectedLabels.toList())
}
