package lang.taxi.lsp.completion

import lang.taxi.lsp.CompilationResult
import lang.taxi.types.*
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.concurrent.atomic.AtomicReference

class TypeProvider(private val lastSuccessfulCompilationResult: AtomicReference<CompilationResult>,
                   private val lastCompilationResult: AtomicReference<CompilationResult>
) {
    private val primitives = PrimitiveType.values()
            .map { type ->
                type to CompletionItem(type.declaration).apply {
                    kind = CompletionItemKind.Class
                    insertText = type.declaration
                    detail = type.typeDoc
                }
            }.toMap()

    fun getTypes(decorators: List<CompletionDecorator> = emptyList(), filter: (QualifiedName, Type?) -> Boolean): List<CompletionItem> {
        val compiledDoc = lastSuccessfulCompilationResult.get()?.document
        val lastSuccessfulCompilationTypeNames = lastSuccessfulCompilationResult.get()?.compiler?.declaredTypeNames()
                ?: emptyList()
        val lastCompilationResultTypeNames = lastCompilationResult.get()?.compiler?.declaredTypeNames() ?: emptyList()
        val typeNames = (lastCompilationResultTypeNames + lastSuccessfulCompilationTypeNames).distinct()

        val completionItems = typeNames.map { name ->
            if (compiledDoc?.containsType(name.fullyQualifiedName) == true) { // == true because of nulls
                name to compiledDoc.type(name.fullyQualifiedName)
            } else {
                name to null
            }
        }
                .filter { (name, type) -> filter(name, type) }
                .map { (name, type) ->
                    val doc = if (type is Documented) {
                        type.typeDoc
                    } else null
                    val completionItem = CompletionItem(name.typeName).apply {
                        kind = CompletionItemKind.Class
                        insertText = name.typeName
                        detail = listOfNotNull(name, doc).joinToString("\n")
                    }

                    decorators.fold(completionItem) { itemToDecorate, decorator -> decorator.decorate(name, type, itemToDecorate) }
                }
        val primitiveCompletions = primitives.filter { (type, _) -> filter(type.toQualifiedName(), type) }
                .map { (_, completionItem) -> completionItem }
        return completionItems + primitiveCompletions
    }

    /**
     * Returns all types, including Taxi primitives
     */
    fun getTypes(decorators: List<CompletionDecorator> = emptyList()): List<CompletionItem> {
        return getTypes(decorators) { _, _ -> true }
    }

    fun getEnumValues(decorators: List<CompletionDecorator>, enumType: String?): List<CompletionItem> {
        if (enumType == null) {
            return listOf()
        }

        val enumTypeQualifiedName = lastCompilationResult.get()?.compiler?.declaredTypeNames()?.firstOrNull { it ->
            it.typeName == enumType || it.fullyQualifiedName == enumType
        }

        val enumType = enumTypeQualifiedName?.let {
            lastSuccessfulCompilationResult.get()?.document?.enumType(it.fullyQualifiedName)
        }

        val completionItems = enumType?.let {
            (it as EnumType).values.map { enumValue ->
                CompletionItem(enumValue.name).apply {
                    kind = CompletionItemKind.Class
                    insertText = enumValue.name
                    detail = listOfNotNull(enumValue.name, enumValue.typeDoc).joinToString("\n")
                }
            }
        }

        return completionItems ?: listOf()
    }

    fun getEnumTypes(decorators: List<CompletionDecorator>): List<CompletionItem> {
        return getTypes(decorators) { _, type -> type is EnumType }
    }
}

interface CompletionDecorator {
    fun decorate(typeName: QualifiedName, type: Type?, completionItem: CompletionItem): CompletionItem
}