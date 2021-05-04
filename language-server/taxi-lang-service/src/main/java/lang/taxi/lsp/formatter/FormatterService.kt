package lang.taxi.lsp.formatter

import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import java.util.concurrent.CompletableFuture

class FormatterService {
    fun getChanges(source: String, options: FormattingOptions): CompletableFuture<MutableList<out TextEdit>> {

        val indent = if (options.isInsertSpaces) {
            (0 until options.tabSize).joinToString(separator = "") { " " }
        } else {
            "\t"
        }
        var indentationLevel = 0
        fun expectedIndentation(): Int {
            return indentationLevel * (options.tabSize - 1)
        }

        fun currentIndentText(): String {
            return (0 until indentationLevel).joinToString(separator = "") { indent }
        }

        fun lineRange(line: Int, start: Int, end: Int): Range {
            return Range(Position(line, start), Position(line, end))
        }

        val lines = source.lines().toMutableList()
        if (lines.isEmpty()) {
            return CompletableFuture.completedFuture(mutableListOf())
        }

        val lineEdits = lines.mapIndexed { lineNumber, line ->
            val lineWithoutComments = line.substringBefore("//").trim()
            var appendEmptyLine = false
            if (lineWithoutComments.endsWith("}")) {
                indentationLevel = (indentationLevel - 1).coerceAtLeast(0);
                if (lines.size > lineNumber + 1 && lines[lineNumber + 1].trim() != "") {
                    appendEmptyLine = true
                }
            }
            val builder = StringBuilder()
            builder.append(line.trim().prependIndent(currentIndentText()))
            if (appendEmptyLine) {
                builder.append("\n")
            }



            if (lineWithoutComments.endsWith("{")) {
                indentationLevel++
            }

            builder.toString()
        }.joinToString("\n")

        val lastPosition = Position(
                (lines.size - 1).coerceAtLeast(0),
                (lines.last().length))


        val edit = TextEdit(Range(Position(0, 0), lastPosition), lineEdits)
        return CompletableFuture.completedFuture(mutableListOf(edit))
    }
}