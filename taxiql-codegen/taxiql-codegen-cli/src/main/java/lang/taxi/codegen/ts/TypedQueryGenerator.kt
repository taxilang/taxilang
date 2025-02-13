package lang.taxi.codegen.ts

import com.google.common.annotations.VisibleForTesting
import lang.taxi.codegen.GeneratedCode
import lang.taxi.query.QueryMode
import lang.taxi.query.TaxiQlQuery
import lang.taxi.types.Arrays
import lang.taxi.utils.quoted
import org.antlr.v4.runtime.CodePointCharStream
import java.nio.file.Paths

typealias TypescriptTypeName = String

class TypedQueryGenerator {

   /**
    * Generates all the query snippets (exporting a query as a TypedQuery<T>)
    * as well as a constant with the queries declared, and the
    * associated taxiQl() functions
    */
   fun generate(queries: Map<TaxiQlQuery, TypescriptTypeName>, typesFilename: String = "types.ts", originalSources: List<CodePointCharStream> = emptyList()): GeneratedCode {
      val snippets = queries.entries.map { (query, returnTypeName) ->

         // When generating a typescript snippet from an inline tql() block, the code in the function
         // needs to exactly match the original source - otherwise the Typescript compiler won't see it as
         // the correct function overload.
         // Given the Taxi compiler has trimmed whitespace, we try to match up the source (from the compilation unit
         // of the query) to the actual source that was passed.
         // This lets us write the correct query string literal to the typescript file.
         val sourceFilename = query.compilationUnits.firstOrNull()?.source?.sourceName
         val originalSource = originalSources.firstOrNull { it.sourceName == sourceFilename }
            ?.toString()?.replace("\r\n", "\n") // we need to normalise CRLF to LF as well
         generateQuerySnippet(query, returnTypeName, typesFilename, originalSource)
      }


      // A code block that looks roughly like:
      // export const queries = {
      //   'find { Film[] }' : FindFilmResult,
      //   // etc.
      // }
      val queriesConstBlock = snippets.joinToString(prefix = "export const queries = {\n  ", separator = ",\n  ", postfix = "\n}") { querySnippet ->
         val querySourceLiteral = convertToTsStringLiteral(querySnippet.querySource)
         "$querySourceLiteral : ${querySnippet.constantName}"
      }

      // A code block that looks roughly like:
      // export function tql(query: "hello world"):TypedQuery<HelloWorldResult>
      // export function tql(query: "hello world 2"):TypedQuery<HelloWorldResult2>
      val taxiQlFunctionOverloadsBlock = snippets.joinToString("\n") { querySnippet->
         val querySourceLiteral = convertToTsStringLiteral(querySnippet.querySource)
         val queryTypeTsName = querySnippet.queryTypeTsName.typeName
         "export function taxiQl(query: $querySourceLiteral): ${queryTypeTsName}<${querySnippet.returnTypeDeclaration}>"
      }
      val taxiQlFunctionImplementationBlock = """
         export function taxiQl<T>(query: string): TypedQuery<T> {
            return (queries as any)[query];
         }
      """.trimIndent()

      val imports = snippets.flatMap { it.imports }
         .distinct()

      val importBlock = imports.joinToString("\n")

      val codeSnippetsAsConsts = snippets.joinToString("\n\n") { it.code }

      val code = listOf(
         importBlock,
         codeSnippetsAsConsts,
         queriesConstBlock,
         taxiQlFunctionOverloadsBlock,
         taxiQlFunctionImplementationBlock
      ).joinToString("\n\n")

      return GeneratedCode(Paths.get("queries.ts"), code)
   }

   private fun replaceLineBreaks(string: String): String {
      // First, replace all CRLF with \\r\\n (literal \r\n)
      var result = string.replace("\r\n", "\\r\\n")
      // Then, replace all LF with \n (literal \n)
      // (only replace LF that are not part of CRLF)
      result = result.replace("\n", "\\n")
      return result
   }

   private fun convertToTsStringLiteral(string: String): String {
      return replaceLineBreaks(string) // replace new lines with literal \n
         .replace("\"", "\\") // replace quotes with escaped quote
         .quoted()
   }

   @VisibleForTesting
   internal fun generateQuerySnippet(
      query: TaxiQlQuery,
      returnTypeTsName: String,
      typesFilename: String = "types.ts",
      originalSource: String?
   ): QuerySnippet {
      val queryName = query.name.typeName
      val (queryType, returnType) = when {
         Arrays.isArray(query.returnType) -> QueryType.RequestResponse to "$returnTypeTsName[]"
         query.queryMode == QueryMode.STREAM -> QueryType.Stream to returnTypeTsName
         else -> QueryType.RequestResponse to returnTypeTsName
      }
      val code = """export const $queryName = {
   taxiQl: `${query.source}`
} as ${queryType.typeName}<$returnType>;
""".trimIndent()

      val imports = listOf(
         queryType.import,
         "import { $returnTypeTsName } from './${typesFilename.removeSuffix(".ts")}';"
      )
      return QuerySnippet(imports, code, queryName, originalSource ?: query.source, returnType, queryType)
   }

}

data class QuerySnippet(
   val imports: List<String>,
   val code: String,
   val constantName: String,
   val querySource: String,
   // The typename, plus the array marker, if req'd
   val returnTypeDeclaration: String,
   val queryTypeTsName: QueryType,
) {
   override fun toString(): String {
      return """${imports.joinToString("\n")}
         |
         |$code
      """.trimMargin()
   }
}

enum class QueryType(
   val typeName: String,
   val import: String = "import { $typeName } from '${TypescriptUtils.TaxiQlPackageName}';"
) {
   RequestResponse("TypedQuery"),
   Stream("TypedStream")
}
