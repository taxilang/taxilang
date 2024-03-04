package lang.taxi

import lang.taxi.accessors.ProjectionFunctionScope
import lang.taxi.mutations.Mutation
import lang.taxi.query.QueryMode
import lang.taxi.query.TaxiQlQuery
import lang.taxi.query.commands.MutationCommand
import lang.taxi.types.Type
import kotlin.test.assertFailsWith
import kotlin.test.fail

fun String.compiledWithQuery(
   query: String,
   config: CompilerConfig = CompilerConfig()
): Pair<TaxiDocument, TaxiQlQuery> {
   val schema = this.compiled()
   val queries = Compiler(source = query, importSources = listOf(schema), config = config)
      .queries()
   return schema to queries.single()
}

fun String.compiledWithQueryProducingCompilationException(
   query: String,
   config: CompilerConfig = CompilerConfig()
): CompilationException {
   return assertFailsWith {
      this.compiledWithQuery(query, config)
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


// Tools for making tests backwards compatible, after
// refactoring query to contain multiple commands
val TaxiQlQuery.projectedType: Type?
   get() {
      return this.finalCommandAsReadOperation.projectedType
   }

val TaxiQlQuery.queryMode: QueryMode
   get() {
      return this.finalCommandAsReadOperation.queryMode
   }

val TaxiQlQuery.mutation: Mutation
   get() {
      return (this.commands.last() as MutationCommand).mutation
   }

val TaxiQlQuery.projectionScopeVars: List<ProjectionFunctionScope>
   get() {
      return this.finalCommandAsReadOperation.projectionScopeVars
   }
