package lang.taxi.lsp.signatures

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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

         val help = service.signatureHelp(SignatureHelpParams(
            workspacePath.document("person.taxi"),
            Position(1,17) // Location of open parenthesis of  firstName : left(
         ))

         help.get().toString().shouldBe("""SignatureHelp [
  signatures = SingletonList (
    SignatureInformation [
      label = "left"
      documentation = Either [
        left = null
        right = MarkupContent [
        kind = "markdown"
        value = "Returns the left most characters from the source string"
      ]
      ]
      parameters = ArrayList (
        ParameterInformation [
          label = Either [
            left = source (lang.taxi.String)
            right = null
          ]
          documentation = Either [
            left = null
            right = MarkupContent [
            kind = "markdown"
            value = ""
          ]
          ]
        ],
        ParameterInformation [
          label = Either [
            left = count (lang.taxi.Int)
            right = null
          ]
          documentation = Either [
            left = null
            right = MarkupContent [
            kind = "markdown"
            value = ""
          ]
          ]
        ]
      )
    ]
  )
  activeSignature = 0
  activeParameter = 0
]""")
//

         val help2 = service.signatureHelp(SignatureHelpParams(
            workspacePath.document("person.taxi"),
            Position(2,17) // Location of open parenthesis of  lastName : left(
         )).get()

         help2.toString().shouldBe("""SignatureHelp [
  signatures = SingletonList (
    SignatureInformation [
      label = "trim"
      documentation = Either [
        left = null
        right = MarkupContent [
        kind = "markdown"
        value = ""
      ]
      ]
      parameters = ArrayList (
        ParameterInformation [
          label = Either [
            left = lang.taxi.String
            right = null
          ]
          documentation = Either [
            left = null
            right = MarkupContent [
            kind = "markdown"
            value = ""
          ]
          ]
        ]
      )
    ]
  )
  activeSignature = 0
  activeParameter = 0
]""")

      }
   }

})
