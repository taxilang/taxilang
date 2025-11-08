package lang.taxi.lsp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import lang.taxi.lsp.completion.C3CompletionProvider
import lang.taxi.lsp.completion.CompilationResultTypeRepository
import lang.taxi.lsp.completion.ImportCompletionDecorator
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Position

/**
 * Tests for the ANTLR4-c3 based completion provider.
 *
 * These tests validate that the C3CompletionProvider correctly uses the grammar
 * to provide syntactic completions, especially in cases where the existing
 * pattern-matching approach fails (e.g., incomplete expressions).
 */
class C3CompletionProviderSpec : DescribeSpec({

    describe("C3CompletionProvider syntactic completions") {

        it("should provide keyword completions at the start of a file") {
            val source = """
                |
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            val completions = service.completion(
                CompletionParams(
                    workspaceRoot.document("test.taxi"),
                    Position(0, 0)
                )
            ).get().left

            completions.shouldNotBeEmpty()
            // Should include top-level keywords like "type", "enum", "service", etc.
            val labels = completions.map { it.label.lowercase() }
            labels.filter { it in listOf("type", "enum", "model", "namespace", "import") }
                .shouldNotBeEmpty()
        }

        it("should provide completions after 'type' keyword") {
            val source = """
                |type
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            val editPosition = source.positionOf("type ", CursorPosition.EndOfText).toPosition()
            val completions = service.completion(
                CompletionParams(
                    workspaceRoot.document("test.taxi"),
                    editPosition
                )
            ).get().left

            completions.shouldNotBeEmpty()
            // After "type", we should get suggestions for identifier or type modifiers
        }

        it("should provide completions inside a type body") {
            val source = """
                |type Person {
                |
                |}
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            val editPosition = source.positionOf("{\n    ", CursorPosition.EndOfText).toPosition()
            val completions = service.completion(
                CompletionParams(
                    workspaceRoot.document("test.taxi"),
                    editPosition
                )
            ).get().left

            // Inside a type body, grammar should suggest field declarations
            completions.size shouldBeGreaterThan 0
        }

        it("should provide completions for incomplete field definitions") {
            val source = """
                |type Person {
                |    name :
                |}
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            val editPosition = source.positionOf("name : ", CursorPosition.EndOfText).toPosition()
            val completions = service.completion(
                CompletionParams(
                    workspaceRoot.document("test.taxi"),
                    editPosition
                )
            ).get().left

            // After field name and colon, grammar should suggest type references
            completions.shouldNotBeEmpty()
        }

        // This is the key test - the scenario that fails with the old approach
        it("should provide completions in incomplete expressions (enum scenario)") {
            val source = """
                |enum Country { US, UK, DE }
                |
                |type Trade {
                |    country: Country
                |    countryCode: String by when(country) {
                |        Country.US -> "US"
                |        Country.UK -> "UK"
                |        else -> Country.
                |    }
                |}
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            val editPosition = source.positionOf("Country.", CursorPosition.EndOfText).toPosition()
            val completions = service.completion(
                CompletionParams(
                    workspaceRoot.document("test.taxi"),
                    editPosition
                )
            ).get().left

            // The C3 provider should at least provide syntactic completions here
            // even if it doesn't provide the specific enum values
            completions.shouldNotBeEmpty()
        }

        it("should provide completions for annotation parameters") {
            val source = """
                |annotation MyAnnotation {
                |    value: String
                |    count: Int
                |}
                |
                |@MyAnnotation(
                |type Foo {}
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            val editPosition = source.positionOf("@MyAnnotation( ", CursorPosition.EndOfText).toPosition()
            val completions = service.completion(
                CompletionParams(
                    workspaceRoot.document("test.taxi"),
                    editPosition
                )
            ).get().left

            // Grammar should suggest valid annotation parameter syntax
            completions.shouldNotBeEmpty()
        }
    }

    describe("C3CompletionProvider integration") {

        it("should work alongside other completion providers") {
            val source = """
                |namespace com.example
                |
                |type Person {
                |    name :
                |}
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            val editPosition = source.positionOf("name : ", CursorPosition.EndOfText).toPosition()
            val completions = service.completion(
                CompletionParams(
                    workspaceRoot.document("test.taxi"),
                    editPosition
                )
            ).get().left

            // Should get completions from both C3 (grammar-based) and other providers (type-based)
            completions.shouldNotBeEmpty()

            // Should include primitive types from semantic providers
            val labels = completions.map { it.label.lowercase() }
            labels.shouldContain("string")
        }
    }

    describe("C3CompletionProvider unit tests") {

        it("should handle null context gracefully") {
            val provider = C3CompletionProvider()
            val source = """
                |type Person {}
            """.trimMargin()

            val (service, workspaceRoot) = documentServiceWithFiles(
                io.kotest.engine.spec.tempdir(),
                files = arrayOf("test.taxi" to source)
            )

            service.compile()
            val compilationResult = service.lastCompilationResult!!
            val typeRepository = CompilationResultTypeRepository(compilationResult, null)
            val importDecorator = ImportCompletionDecorator(
                compilationResult.compiler,
                workspaceRoot.resolve("test.taxi").toString()
            )

            // Should not throw even with null context
            val result = provider.getCompletionsForContext(
                compilationResult = compilationResult,
                params = CompletionParams(workspaceRoot.document("test.taxi"), Position(0, 0)),
                importDecorator = importDecorator,
                contextAtCursor = null,
                lastSuccessfulCompilation = null,
                typeRepository = typeRepository
            ).get()

            // Even with null context, should try to provide completions
            result.completions.shouldNotBeEmpty()
        }
    }
})
