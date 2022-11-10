package lang.taxi

import lang.taxi.query.TaxiQlQuery
import kotlin.test.assertFailsWith
import kotlin.test.fail

fun String.compiledWithQuery(query: String): Pair<TaxiDocument, TaxiQlQuery> {
   val schema = this.compiled()
   val queries = Compiler(source = query, importSources = listOf(schema)).queries()
   return schema to queries.single()
}

fun String.compiledWithQueryProducingCompilationException(query: String): CompilationException {
   return assertFailsWith {
      this.compiledWithQuery(query)
   }
}

fun List<CompilationError>.shouldContainMessage(message: String) {
   if (this.any { it.detailMessage == message }) return
   val failure = "Expected a compilation message with message $message."
   if (this.isEmpty()) {
      fail("$failure  There were no compilation messages present")
   } else {
      fail("$failure  Instead, the following message(s) were present: \n${this.joinToString("\n") { it.detailMessage }}")
   }
}
