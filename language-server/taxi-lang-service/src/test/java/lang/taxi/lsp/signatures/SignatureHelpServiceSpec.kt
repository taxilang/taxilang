package lang.taxi.lsp.signatures

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import lang.taxi.lsp.CursorPosition
import lang.taxi.lsp.LspServicesConfig
import lang.taxi.lsp.actions.CodeActionService
import lang.taxi.lsp.actions.ExtractInlineType
import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceFor
import lang.taxi.lsp.documentServiceWithFiles
import lang.taxi.lsp.positionOf
import lang.taxi.lsp.toPosition
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SignatureHelpParams

class SignatureHelpServiceSpec : DescribeSpec({
   describe("signature help") {
      val languageServiceConfig = LspServicesConfig(
      )


      describe("error recovery") {
         it("should offer annotation completions if the doc is invalid") {
            val source = """annotation Sample {
   textA : String
   textB : String
}

@Sample( textA = "" , ) // <--- Cursor is after the comma
model Person {}
            """
            val (service, workspacePath) = documentServiceWithFiles(
               tempdir(),
               languageServiceConfig,
               "person.taxi" to source
            )
            val position = source.positionOf("""@Sample( textA = "" ,""", CursorPosition.EndOfText).toPosition()
            val help = service.signatureHelp(
               SignatureHelpParams(
                  workspacePath.document("person.taxi"),
                  position
               )
            )
            help.shouldNotBeNull()
         }
      }

      it("should provide signature help when inside a function") {

         val (service, workspacePath) = documentServiceWithFiles(
            tempdir(),
            languageServiceConfig,
            "person.taxi" to """model Person {
               |firstName : left() // test empty function
               |lastName : left(trim(" hello "), 2) // test nested functions
               |}
            """.trimMargin()
         )

         val help = service.signatureHelp(
            SignatureHelpParams(
               workspacePath.document("person.taxi"),
               Position(1, 17) // Location of open parenthesis of  firstName : left(
            )
         )
          val singatures = help.get().signatures
          singatures.shouldHaveSize(1)
          singatures.single().label.shouldBe("left(source: String, count: Int)")
          singatures.single().parameters.shouldHaveSize(2)
      }
   }

})
