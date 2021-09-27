package lang.taxi

import lang.taxi.query.TaxiQlQuery

fun String.compiledWithQuery(query:String):Pair<TaxiDocument,TaxiQlQuery> {
   val schema = this.compiled()
   val queries = Compiler(source = query, importSources = listOf(schema)).queries()
   return schema to queries.single()
}
