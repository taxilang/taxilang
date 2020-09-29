package lang.taxi

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Table
import com.google.common.collect.TreeBasedTable
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.ParserRuleContext
import java.net.URI

typealias RowIndex = Int // 0 Based
typealias ColumnIndex = Int // 0 Based
typealias TokenTable = Table<RowIndex, ColumnIndex, ParserRuleContext>


class TokenStore {
   private val tables = mutableMapOf<String, TokenTable>()
   private val typeReferencesBySourceName = ArrayListMultimap.create<String,TaxiParser.TypeTypeContext>()
   fun tokenTable(sourceName: String): TokenTable {
      val sourcePath = SourceNames.normalize(sourceName)
      if (!tables.containsKey(sourcePath)) {
         error("$sourceName has not been normalized properly.  Current keys are ${tables.keys.joinToString(",")}")
      }
      return tables.getValue(sourcePath)
   }

   fun getTypeReferencesForSourceName(sourceName:String):List<TaxiParser.TypeTypeContext> {
      val normalized = SourceNames.normalize(sourceName)
      return typeReferencesBySourceName[normalized]
   }

   fun insert(sourceName: String, rowNumber: RowIndex, columnIndex: ColumnIndex, context: ParserRuleContext) {
      val sourcePath = SourceNames.normalize(sourceName)
      tables.getOrPut(sourcePath, { TreeBasedTable.create() })
         .put(rowNumber, columnIndex, context)

      if (context is TaxiParser.TypeTypeContext) {
         typeReferencesBySourceName[sourceName].add(context)
      }
   }

   operator fun plus(other: TokenStore): TokenStore {
      val result = TokenStore()
      result.tables.putAll(this.tables)
      result.tables.putAll(other.tables)
      result.typeReferencesBySourceName.putAll(this.typeReferencesBySourceName)
      result.typeReferencesBySourceName.putAll(other.typeReferencesBySourceName)
      return result
   }

}
