package lang.taxi.lsp.completion

import lang.taxi.lsp.CompilationResult
import lang.taxi.types.AnnotationType
import lang.taxi.types.Arrays
import lang.taxi.types.Documented
import lang.taxi.types.EnumType
import lang.taxi.types.ImportableToken
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeKind
import lang.taxi.utils.log
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.concurrent.atomic.AtomicReference

class TypeProvider(
   private val lastSuccessfulCompilationResult: AtomicReference<CompilationResult>,
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

   fun getTypes(
      decorators: List<CompletionDecorator> = emptyList(),
      filter: (QualifiedName, Type?) -> Boolean
   ): List<CompletionItem> {
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
         .map { (name, type) -> buildCompletionItem(type, name, decorators) }
      val primitiveCompletions = primitives.filter { (type, _) -> filter(type.toQualifiedName(), type) }
         .map { (_, completionItem) -> completionItem }
      return completionItems + primitiveCompletions
   }

   fun buildCompletionItem(
      type: Type,
      decorators: List<CompletionDecorator>
   ): CompletionItem {
      return buildCompletionItem(type, type.toQualifiedName(), decorators)
   }

   fun buildCompletionItem(
      type: Type?,
      name: QualifiedName,
      decorators: List<CompletionDecorator>
   ): CompletionItem {
      val doc = if (type is Documented) {
         type.typeDoc
      } else null
      val typeName = when {
         Arrays.isArray(name) -> name.parameters[0].typeName + "[]"
         StreamType.isStreamTypeName(name) -> name.parameters[0].typeName
         else -> name.typeName
      }
      val namespace = name.namespace.let { namespace ->
         if (namespace.isNotEmpty()) {
            " ($namespace)"
         } else ""
      }
      val label = "$typeName$namespace"
      val completionItemKind = when {
         type == null -> {
            CompletionItemKind.Unit
         } // Not sure what to pass here
         type.typeKind == null -> {
            CompletionItemKind.Unit
         } // Why would this be null?
         type is AnnotationType -> CompletionItemKind.Interface // ? There isn't an annotation CompletionItemKind :(
         type.typeKind!! == TypeKind.Model -> CompletionItemKind.Class
         type.typeKind!! == TypeKind.Type -> CompletionItemKind.Field
         else -> {
            log().debug("Unhandled switch case in buildCompletionItem")
            CompletionItemKind.Field
         }
      }
      val (completionFilterText, completionInsertText) = if (type is AnnotationType) {
         "@$typeName" to "@$typeName"
      } else null to typeName

      val completionItem = CompletionItem(label).apply {
         kind = completionItemKind
         insertText = completionInsertText
         detail = listOfNotNull(typeName, doc).joinToString("\n")
         documentation = listOfNotNull(typeName, doc).joinToString("\n").toMarkup()
         filterText = completionFilterText

      }

      return decorators.fold(completionItem) { itemToDecorate, decorator ->
         decorator.decorate(
            name,
            type,
            itemToDecorate
         )
      }
   }

   /**
    * Returns all types, including Taxi primitives
    */
   fun getTypes(decorators: List<CompletionDecorator> = emptyList()): List<CompletionItem> {
      return getTypes(decorators) { _, _ -> true }
   }

   fun getTypeName(text: String): QualifiedName? {
      return lastCompilationResult.get()?.compiler?.declaredTypeNames()?.firstOrNull { it ->
         it.typeName == text || it.fullyQualifiedName == text
      }
   }

   fun getEnumValues(decorators: List<CompletionDecorator>, enumTypeName: QualifiedName): List<CompletionItem> {
      val enumType =
         lastSuccessfulCompilationResult.get()?.document?.enumType(enumTypeName.fullyQualifiedName)

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

   fun getEnumValues(decorators: List<CompletionDecorator>, enumTypeName: String?): List<CompletionItem> {
      if (enumTypeName == null) {
         return listOf()
      }


      val enumTypeQualifiedName = getTypeName(enumTypeName) ?: return emptyList()
      return getEnumValues(decorators, enumTypeName)

   }

   fun getEnumTypes(decorators: List<CompletionDecorator>): List<CompletionItem> {
      return getTypes(decorators) { _, type -> type is EnumType }
   }
}

interface CompletionDecorator {
   fun decorate(typeName: QualifiedName, token: ImportableToken?, completionItem: CompletionItem): CompletionItem
}

fun CompletionItem.decorate(decorators: List<CompletionDecorator>, name: QualifiedName, token: ImportableToken):CompletionItem {
   return decorators.fold(this) { itemToDecorate, decorator ->
      decorator.decorate(
         name,
         token,
         itemToDecorate
      )
   }
}
