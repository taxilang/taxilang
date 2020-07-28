package lang.taxi.lsp.completion

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.jsonrpc.messages.Either

object TopLevelCompletions {
    private val type = CompletionItem().apply {
        insertText = """type $1 {
            |   $0
            |}""".trimMargin()
        kind = CompletionItemKind.Class
        insertTextFormat = InsertTextFormat.Snippet
        documentation = Either.forRight(markdown("""Defines a new type in the taxonomy.
            |
            |examples: 
            |
            |```
            |type Person {
            |   firstName : FirstName as String // Adding an inline type-alias
            |   lastName : LastName // Using a type already declared elsewhere
            |}
            |```
        """.trimMargin()))
        label = "type"
    }
    private val enum = CompletionItem().apply {
        insertText = """enum $1 {
            |   $0
            |}""".trimMargin()
        kind = CompletionItemKind.Enum
        insertTextFormat = InsertTextFormat.Snippet
        documentation = Either.forRight(markdown("""Defines a new enumerated type in the taxonomy.
            |
            |Use enums when you have a predefined set of valid values. 
        """.trimMargin()))
        label = "enum"
    }

    private val typeAlias = CompletionItem().apply {
        insertText = """type alias $0 as $1""".trimMargin()
        kind = CompletionItemKind.Enum
        insertTextFormat = InsertTextFormat.Snippet
        documentation = Either.forRight(markdown("""Defines a type alias.
            |
            |Use type aliases where two types are semantically the same, and 
            |can be used interchangeably.
        """.trimMargin()))
        label = "type alias"
    }

    private val service = CompletionItem().apply {
        insertText = """service $1 {
            |   $0
            |}
        """.trimMargin()
        kind = CompletionItemKind.Enum
        insertTextFormat = InsertTextFormat.Snippet
        documentation = Either.forRight(markdown("""Defines a type alias.
            |
            |Use type aliases where two types are semantically the same, and 
            |can be used interchangeably.
        """.trimMargin()))
        label = "type alias"
    }


    val topLevelCompletionItems: List<CompletionItem> = listOf(
            type, enum, typeAlias, service
    )
}