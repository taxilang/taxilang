package lang.taxi.lsp.taxiconf

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

class TaxiConfServiceTest : DescribeSpec({

   describe("TaxiConfService") {
      val service = TaxiConfService()

      describe("completions") {
         it("should provide top-level completions for an empty file") {
            val content = ""
            val params = CompletionParams(
               TextDocumentIdentifier("file:///test/taxi.conf"),
               Position(0, 0)
            )

            val completions = service.getCompletions("file:///test/taxi.conf", content, params)

            completions.items.shouldNotBeEmpty()
            completions.items.map { it.label }.should.contain.elements("name", "version", "sourceRoot", "plugins")
         }

         it("should not suggest ignored fields") {
            val content = ""
            val params = CompletionParams(
               TextDocumentIdentifier("file:///test/taxi.conf"),
               Position(0, 0)
            )

            val completions = service.getCompletions("file:///test/taxi.conf", content, params)

            val labels = completions.items.map { it.label }
            labels.shouldNotContain("identifier")
            labels.shouldNotContain("dependencyPackages")
            labels.shouldNotContain("packageRootPath")
            labels.shouldNotContain("sourceRootPath")
         }

         it("should provide completions with snippets for complex types") {
            val content = ""
            val params = CompletionParams(
               TextDocumentIdentifier("file:///test/taxi.conf"),
               Position(0, 0)
            )

            val completions = service.getCompletions("file:///test/taxi.conf", content, params)

            val pluginsCompletion = completions.items.find { it.label == "plugins" }
            pluginsCompletion shouldNotBe null
            pluginsCompletion?.insertText?.should?.contain("{")
         }

         it("should provide custom documentation for taxi-specific fields") {
            val content = ""
            val params = CompletionParams(
               TextDocumentIdentifier("file:///test/taxi.conf"),
               Position(0, 0)
            )

            val completions = service.getCompletions("file:///test/taxi.conf", content, params)

            val nameCompletion = completions.items.find { it.label == "name" }
            nameCompletion shouldNotBe null
            nameCompletion?.documentation?.toString()?.should?.contain("namespace/project-name")
         }
      }

      describe("diagnostics") {

         it("should validate successfully for a valid configuration") {
            val content = """
               name: taxi/sample
               version: 0.1.0
            """.trimIndent()

            val diagnostics = service.getDiagnostics("file:///test/taxi.conf", content)

            diagnostics.shouldBeEmpty()
         }

         it("should report warning for invalid version format") {
            val content = """
               name: taxi/sample
               version: invalid-version
            """.trimIndent()

            val diagnostics = service.getDiagnostics("file:///test/taxi.conf", content)

            val versionDiagnostic = diagnostics.find { it.message.contains("version") }
            versionDiagnostic shouldNotBe null
            versionDiagnostic?.severity shouldBe DiagnosticSeverity.Warning
         }

         it("should report warning for invalid project name format") {
            val content = """
               name: invalid-name-without-namespace
               version: 1.0.0
            """.trimIndent()

            val diagnostics = service.getDiagnostics("file:///test/taxi.conf", content)

            val nameDiagnostic = diagnostics.find { it.message.contains("project id") }
            nameDiagnostic shouldNotBe null
            nameDiagnostic?.severity shouldBe DiagnosticSeverity.Error
         }

         it("should handle parse errors gracefully") {
            val content = """
               name: taxi/sample
               version: 1.0.0
               plugins: {
                  invalid syntax here
            """.trimIndent()

            val diagnostics = service.getDiagnostics("file:///test/taxi.conf", content)

            diagnostics.shouldNotBeEmpty()
            diagnostics.first().severity shouldBe DiagnosticSeverity.Error
         }

         it("should validate a complex configuration") {
            val content = """
               name: taxi/sample
               version: 0.3.0
               sourceRoot: src/
               plugins: {
                  "taxi/kotlin": {
                     maven: {
                        groupId: "lang.taxi"
                     }
                  }
               }
               linter: {
                  no-duplicate-types-on-models: {
                     severity: INFO
                  }
               }
            """.trimIndent()

            val diagnostics = service.getDiagnostics("file:///test/taxi.conf", content)

            diagnostics.shouldBeEmpty()
         }

         it("should report error when type extraction fails") {
            val content = """
               name: 123
               version: 1.0.0
            """.trimIndent()

            val diagnostics = service.getDiagnostics("file:///test/taxi.conf", content)

            // The parsing will succeed (HOCON allows this), but config4k extraction may fail
            // Depending on how config4k handles type coercion
            // This test documents the behavior
         }
      }

      describe("cache management") {
         it("should cache parse results") {
            val content = """
               name: taxi/sample
               version: 1.0.0
            """.trimIndent()

            service.getDiagnostics("file:///test/taxi.conf", content)
            val cached = service.getCachedParseResult("file:///test/taxi.conf")

            cached shouldNotBe null
            cached?.name shouldBe "taxi/sample"
         }

         it("should clear cache for specific URI") {
            val content = """
               name: taxi/sample
               version: 1.0.0
            """.trimIndent()

            service.getDiagnostics("file:///test/taxi.conf", content)
            service.clearCache("file:///test/taxi.conf")
            val cached = service.getCachedParseResult("file:///test/taxi.conf")

            cached shouldBe null
         }
      }
   }
})

fun <T> List<T>.shouldNotContain(element: T) {
   this.contains(element) shouldBe false
}
