package lang.taxi.lsp.highlighting

import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.lsp.document
import lang.taxi.lsp.documentServiceFor
import org.eclipse.lsp4j.SemanticTokensParams

class SemanticTokensServiceSpec :  DescribeSpec({
   it("should provide highlighting for a full file") {
      val (service, workspaceRoot) = documentServiceFor("test-scenarios/semantic-tokens-workspace")

      val completions = service.semanticTokensFull(
         SemanticTokensParams(
            workspaceRoot.document("trade.taxi"),
         )
      ).get()
   }
})
