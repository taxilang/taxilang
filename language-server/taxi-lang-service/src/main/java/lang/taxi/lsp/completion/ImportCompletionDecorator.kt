package lang.taxi.lsp.completion

import lang.taxi.Compiler
import lang.taxi.types.ImportableToken
import lang.taxi.types.QualifiedName
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit

class ImportCompletionDecorator(compiler: Compiler, sourceUri: String) : CompletionDecorator {
   val typesDeclaredInFile = compiler.typeNamesForSource(sourceUri)
   val importsDeclaredInFile = compiler.importedTypesInSource(sourceUri)

   override fun decorate(typeName: QualifiedName, token: ImportableToken?, completionItem: CompletionItem): CompletionItem {
      // TODO : Insert after other imports
      val insertPosition = Range(
          Position(0, 0),
          Position(0, 0)
      )
      if (completionItem.additionalTextEdits == null) {
         completionItem.additionalTextEdits = mutableListOf()
      }
      if (!typesDeclaredInFile.contains(typeName) && !importsDeclaredInFile.contains(typeName)) {
         completionItem.additionalTextEdits.add(
             TextEdit(
                 insertPosition,
                 "import ${typeName.firstTypeParameterOrSelf}\n"
             )
         )
      }
      return completionItem
   }

}
