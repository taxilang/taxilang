package lang.taxi.lsp.completion

import lang.taxi.services.Service
import lang.taxi.types.AnnotationType
import lang.taxi.types.Arrays
import lang.taxi.types.Documented
import lang.taxi.types.EnumType
import lang.taxi.types.ImportableToken
import lang.taxi.types.Named
import lang.taxi.types.PrimitiveType
import lang.taxi.types.QualifiedName
import lang.taxi.types.StreamType
import lang.taxi.types.Type
import lang.taxi.types.TypeKind
import lang.taxi.utils.log
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind


class TypeCompletionBuilder(
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
      typeRepository: TypeRepository,
      decorators: List<CompletionDecorator> = emptyList(),
      filter: (QualifiedName, Type?) -> Boolean,
   ): List<CompletionItem> {
      val completionItems = try {
         typeRepository.getTypeNames()
            .filter { (name, type) -> filter(name, type) }
            .map { (name, type) -> buildCompletionItem(type, name, decorators) }
      } catch (e:Exception) {
         emptyList()
      }
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
      type: Named?,
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
            CompletionItemKind.Class
         } // Not sure what to pass here
         type is Type && type.typeKind == null -> {
            CompletionItemKind.Class
         } // Why would this be null?
         type is AnnotationType -> CompletionItemKind.Interface // ? There isn't an annotation CompletionItemKind :(
         type is Type && type.typeKind!! == TypeKind.Model -> CompletionItemKind.Interface
         type is Type && type.typeKind!! == TypeKind.Type -> CompletionItemKind.Field
         type is Service -> CompletionItemKind.Class
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
   fun getTypes(typeRepository: TypeRepository, decorators: List<CompletionDecorator> = emptyList()): List<CompletionItem> {
      return getTypes(typeRepository, decorators) { _, _ -> true }
   }

   fun getTypeName(typeRepository: TypeRepository, text: String): QualifiedName? {
      return typeRepository.getTypeName(text)
   }

   fun getEnumValues(typeRepository: TypeRepository, decorators: List<CompletionDecorator>, enumTypeName: QualifiedName): List<CompletionItem> {
      val enumType = typeRepository.getEnumType(enumTypeName.fullyQualifiedName)
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

   fun getEnumValues(typeRepository: TypeRepository, decorators: List<CompletionDecorator>, enumTypeName: String?): List<CompletionItem> {
      if (enumTypeName == null) {
         return listOf()
      }


      val enumTypeQualifiedName = getTypeName(typeRepository, enumTypeName) ?: return emptyList()
      return getEnumValues(typeRepository, decorators, enumTypeName)

   }

   fun getEnumTypes(typeRepository: TypeRepository, decorators: List<CompletionDecorator>): List<CompletionItem> {
      return getTypes(typeRepository, decorators) { _, type -> type is EnumType }
   }
}

interface CompletionDecorator {
   fun decorate(typeName: QualifiedName, token: Named?, completionItem: CompletionItem): CompletionItem
}

fun CompletionItem.decorate(
   decorators: List<CompletionDecorator>,
   name: QualifiedName,
   token: ImportableToken
): CompletionItem {
   return decorators.fold(this) { itemToDecorate, decorator ->
      decorator.decorate(
         name,
         token,
         itemToDecorate
      )
   }
}
