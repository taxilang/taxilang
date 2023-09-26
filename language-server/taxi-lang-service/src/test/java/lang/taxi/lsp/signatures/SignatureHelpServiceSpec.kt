package lang.taxi.lsp.signatures

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import lang.taxi.lsp.LspServicesConfig
import lang.taxi.lsp.actions.CodeActionService
import lang.taxi.lsp.actions.ExtractInlineType
import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceFor
import lang.taxi.lsp.documentServiceWithFiles
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SignatureHelpParams

class SignatureHelpServiceSpec : DescribeSpec({
   describe("signature help") {
      val languageServiceConfig = LspServicesConfig(
      )
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

//         val help = service.signatureHelp(SignatureHelpParams(
//            workspacePath.document("person.taxi"),
//            Position(1,17) // Location of open parenthesis of  firstName : left(
//         ))
//
         // TODO assert

         val help2 = service.signatureHelp(SignatureHelpParams(
            workspacePath.document("person.taxi"),
            Position(2,17) // Location of open parenthesis of  lastName : left(
         ))

         TODO()

      }
   }

})
