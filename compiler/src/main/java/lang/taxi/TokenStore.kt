package lang.taxi

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Table
import com.google.common.collect.TreeBasedTable
import lang.taxi.TaxiParser.TypeReferenceContext
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Compiled
import lang.taxi.types.QualifiedName
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.ParserRuleContext

typealias RowIndex = Int // 0 Based
typealias ColumnIndex = Int // 0 Based
typealias TokenTable = Table<RowIndex, ColumnIndex, ParserRuleContext>

/**
 * An exception thrown if we receive a reference to a source file that we don't know about.
 * This generally happens when editing in VSCode, as we can receive uri references to files in inconsistent
 * formats, and we need to try to normalize them, so they point to the same fle.
 */
class UnknownTokenReferenceException(val providedSourcePath: String, val currentKeys: Collection<String>) :
   RuntimeException(
      "$providedSourcePath is not present in the token store.  Current keys are ${currentKeys.joinToString(",")}"
   )

class TokenStore(
   private val tables: MutableMap<String, TokenTable> = mutableMapOf<String, TokenTable>(),
   private val typeReferencesBySourceName: ArrayListMultimap<String, TypeReferenceContext> = ArrayListMultimap.create<String, TypeReferenceContext>(),
   private val namedSymbolDeclarations: ArrayListMultimap<String, ParserRuleContext> = ArrayListMultimap.create<String, ParserRuleContext>()
) {

   companion object {
      fun combine(members: List<TokenStore>): TokenStore {
         val tables: MutableMap<String, TokenTable> = mutableMapOf()
         val typeReferencesBySourceName: ArrayListMultimap<String, TypeReferenceContext> =
            ArrayListMultimap.create<String, TypeReferenceContext>()

         val namedSymbolDeclarations = ArrayListMultimap.create<String, ParserRuleContext>()
         members.forEach { tokenStore ->
            tables.putAll(tokenStore.tables)
            typeReferencesBySourceName.putAll(tokenStore.typeReferencesBySourceName)
            namedSymbolDeclarations.putAll(tokenStore.namedSymbolDeclarations)
         }
         return TokenStore(tables, typeReferencesBySourceName, namedSymbolDeclarations)
      }
   }

   /**
    * Returns a list of declarations found in these sources where the symbol is
    * defined multiple times. Does not consider imported sources.
    */
   fun collectDuplicateDeclarationsDetectedInSources(): Map<String, Collection<ParserRuleContext>> {
      val declaredInDocumentMultipleTimes = namedSymbolDeclarations.asMap()
         .filter { (name, declarations) -> declarations.size > 1 }
      return declaredInDocumentMultipleTimes
   }

   /**
    * Returns a list of declarations found in this source which conflict
    * with existing declarations from imported sources (dependencies, other compiled docs).
    *
    * Returned structure is a map
    *  - Key: name of conflciting symbol
    *  - Value: Pair - All the places this conflict was detected in the sources being compiled
    *                - The places the symbol was declared in the imported sources
    */
   fun collectDuplicateDeclarationsDetectedInImports(
      importSources: List<TaxiDocument>,
      builtInCompiledTaxi: TaxiDocument
   ): Map<String, Pair<Collection<ParserRuleContext>, List<CompilationUnit>>> {
      val declarationsMap = namedSymbolDeclarations.asMap()
      val duplicatesDeclaredInImports = declarationsMap
         // exclude built-ins
         .filter { (name, _) -> builtInCompiledTaxi.namedSymbolOrNull(name) == null }
         .flatMap { (name, declarationsInCodeBeingCompiled) ->
            val existingDeclarations = importSources.mapNotNull { importSource ->
               importSource.namedSymbolOrNull(name)?.let { symbol ->
                  // This shouldn't happen -- all things should be Compiled
                  require(symbol is Compiled) { "Found a Named instance which is not a Compiled reference - is an instance of ${symbol::class.simpleName}" }
                  name to (declarationsInCodeBeingCompiled to symbol.compilationUnits)
               }
            }
            existingDeclarations
         }.toMap()
      return duplicatesDeclaredInImports
   }

   fun tokenTable(sourceName: String): TokenTable {
      val sourcePath = SourceNames.normalize(sourceName)
      if (!tables.containsKey(sourcePath)) {
         throw UnknownTokenReferenceException(sourceName, tables.keys)
      }
      return tables.getValue(sourcePath)
   }

   fun containsTokensForSource(sourceName: String): Boolean {
      val sourcePath = SourceNames.normalize(sourceName)
      return tables.containsKey(sourcePath)
   }

   fun getTypeReferencesForSourceName(sourceName: String): List<TypeReferenceContext> {
      val normalized = SourceNames.normalize(sourceName)
      return typeReferencesBySourceName[normalized]
   }

   fun insert(sourceName: String, rowNumber: RowIndex, columnIndex: ColumnIndex, context: ParserRuleContext) {
      val sourcePath = SourceNames.normalize(sourceName)
      tables.getOrPut(sourcePath, { TreeBasedTable.create() })
         .put(rowNumber, columnIndex, context)

      appendNamedSymbolReference(context)
      if (context is TypeReferenceContext) {
         typeReferencesBySourceName[sourceName].add(context)
      }
   }

   /**
    * If the token is declaring a new named symbol (eg., a type, a service, a policy),
    * we capture the name and declaration so that later we can detect redeclarations
    */
   private fun appendNamedSymbolReference(context: ParserRuleContext) {
      val name = when (context) {
         is TaxiParser.TypeAliasDeclarationContext -> context.identifier().fullyQualified()
         is TaxiParser.TypeDeclarationContext -> context.identifier().fullyQualified()
         is TaxiParser.EnumDeclarationContext -> context.identifier().fullyQualified()
         is TaxiParser.ServiceDeclarationContext -> context.identifier().fullyQualified()
         is TaxiParser.FunctionDeclarationContext -> context.identifier().fullyQualified()
         is TaxiParser.PolicyDeclarationContext -> context.identifier().fullyQualified()
         is TaxiParser.NamedQueryContext -> context.queryName().identifier().fullyQualified()
         is TaxiParser.AnnotationTypeDeclarationContext -> context.identifier().fullyQualified()
         is TaxiParser.InlineInheritedTypeContext -> {
            val fieldDeclaration = context.searchUpForRule<TaxiParser.FieldTypeDeclarationContext>()
            val declaredTypeName = fieldDeclaration?.typeExpression()?.nullableTypeReference()?.typeReference()
               ?.qualifiedName()?.text?.let { declaredTypeName ->
                  val namespace = context.findNamespace()
                  QualifiedName(namespace, declaredTypeName).parameterizedName
               }
            declaredTypeName
         }

         else -> null
      }

      if (name != null) {
         namedSymbolDeclarations.put(name, context)
      }

   }
}

fun TaxiParser.IdentifierContext.fullyQualified(): String {
   val ns = this.findNamespace()
   val typeName = this.text
   return QualifiedName(ns, typeName).parameterizedName
}
