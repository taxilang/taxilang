package lang.taxi

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Table
import com.google.common.collect.TreeBasedTable
import lang.taxi.TaxiParser.TypeReferenceContext
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
   private val typeReferencesBySourceName: ArrayListMultimap<String, TypeReferenceContext> = ArrayListMultimap.create<String, TypeReferenceContext>()
) {

   companion object {
      fun combine(members: List<TokenStore>): TokenStore {
         val tables: MutableMap<String, TokenTable> = mutableMapOf()
         val typeReferencesBySourceName: ArrayListMultimap<String, TypeReferenceContext> =
            ArrayListMultimap.create<String, TypeReferenceContext>()

         members.forEach { tokenStore ->
            tables.putAll(tokenStore.tables)
            typeReferencesBySourceName.putAll(tokenStore.typeReferencesBySourceName)
         }
         return TokenStore(tables, typeReferencesBySourceName)
      }
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

   fun getTypeReferencesForSourceName(sourceName: String): List<TaxiParser.TypeReferenceContext> {
      val normalized = SourceNames.normalize(sourceName)
      return typeReferencesBySourceName[normalized]
   }

   fun insert(sourceName: String, rowNumber: RowIndex, columnIndex: ColumnIndex, context: ParserRuleContext) {
      val sourcePath = SourceNames.normalize(sourceName)
      tables.getOrPut(sourcePath, { TreeBasedTable.create() })
         .put(rowNumber, columnIndex, context)

      if (context is TaxiParser.TypeReferenceContext) {
         typeReferencesBySourceName[sourceName].add(context)
      }
   }
}
