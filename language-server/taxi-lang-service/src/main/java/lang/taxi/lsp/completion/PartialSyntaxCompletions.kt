package lang.taxi.lsp.completion

import lang.taxi.TaxiParser
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree

fun isIncompleteFieldDefinition(tree:ParseTree?):Boolean {
    return tree is ErrorNode &&
            tree.text == ":" &&
            tree.parent is TaxiParser.TypeBodyContext
}