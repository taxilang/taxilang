package lang.taxi.codegen.ts

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.codegen.GeneratedCode
import lang.taxi.codegen.ts.react.ReactCodegen
import lang.taxi.compiled
import lang.taxi.utils.PathGlob
import java.nio.file.Paths
import kotlin.io.path.name

class TypedQueryGeneratorSpec : DescribeSpec({
    describe("TypedQueryGenerator") {
       val schema = """
          model Film {
            title : Title inherits String
         }
       """.compiled()
       it("generated typed query for request/response query returning array") {
          val query = """
query FindFilms {
   find { Film[] }
}
          """.trimIndent()
         generatedQuery(schema, query, "FindFilmResult")
            .shouldGenerate("""
import { TypedQuery } from '@orbitalhq/taxiql-client';
import { FindFilmResult } from './types';

export const FindFilms = {
   taxiQl: `$query`
} as TypedQuery<FindFilmResult[]>;""".trimIndent())
       }

       it("generated typed query for request/response query returning object") {
          val query = """
query FindFilms {
   find { Film }
}
          """.trimIndent()
          generatedQuery(schema, query, "FindFilmResult")
             .shouldGenerate("""
import { TypedQuery } from '@orbitalhq/taxiql-client';
import { FindFilmResult } from './types';

export const FindFilms = {
   taxiQl: `$query`
} as TypedQuery<FindFilmResult>;""".trimIndent())
       }

       it("generated typed stream for streaming query") {
          val query = """
query FindFilms {
   stream { Film }
}
          """.trimIndent()
          generatedQuery(schema, query, "FindFilmResult")
             .shouldGenerate("""
import { TypedStream } from '@orbitalhq/taxiql-client';
import { FindFilmResult } from './types';

export const FindFilms = {
   taxiQl: `$query`
} as TypedStream<FindFilmResult>;""".trimIndent())
       }


       it("generates queries file") {
          val querySrc = """
             query FindFilms {
                // a comment in here
                find { Film }
                /* a comment here */
             }
          """.trimIndent()
          val taxiQL = Compiler(querySrc, importSources = listOf(schema)).compile()
             .queries.single()
          TypedQueryGenerator().generate(mapOf(taxiQL to "FindFilmsResult"))
             .shouldGenerate("""import { TypedQuery } from '@orbitalhq/taxiql-client';
import { FindFilmsResult } from './types';

export const FindFilms = {
   taxiQl: `query FindFilms {
   // a comment in here
   find { Film }
   /* a comment here */
}`
} as TypedQuery<FindFilmsResult>;

export const queries = {
  "query FindFilms {\n   // a comment in here\n   find { Film }\n   /* a comment here */\n}" : FindFilms
}

export function taxiQl(query: "query FindFilms {\n   // a comment in here\n   find { Film }\n   /* a comment here */\n}"): TypedQuery<FindFilmsResult>

export function taxiQl<T>(query: string): TypedQuery<T> {
   return (queries as any)[query];
}""")
       }

       it("inline queries are written to query strings verbatim") {
          val generated = ReactCodegen()
             .generate(
                querySourcesGlob = PathGlob(Paths.get("."),"doNotMatchAnything"),
                schema = schema,
                inlineSources = listOf(
                   """// this query starts with a comment
                   |query FindFilms {
                   |   find { Film }
                   |}
                   |// and ends with an empty line
                   |
                """.trimMargin(),
                   """|
                      |
                      | // this query has a few empty blank lines to start
                      | query FindFilms2 { find { Film } }""".trimMargin()
                   )
             ).getOrNull()!!
             .single { it.path.name == "queries.ts" }
          generated.content.shouldBe("""import { TypedQuery } from '@orbitalhq/taxiql-client';
import { Film } from './types';

export const FindFilms = {
   taxiQl: `query FindFilms {
   find { Film }
}`
} as TypedQuery<Film>;

export const FindFilms2 = {
   taxiQl: `query FindFilms2 { find { Film } }`
} as TypedQuery<Film>;

export const queries = {
  "// this query starts with a comment\nquery FindFilms {\n   find { Film }\n}\n// and ends with an empty line\n" : FindFilms,
  "\n\n // this query has a few empty blank lines to start\n query FindFilms2 { find { Film } }" : FindFilms2
}

export function taxiQl(query: "// this query starts with a comment\nquery FindFilms {\n   find { Film }\n}\n// and ends with an empty line\n"): TypedQuery<Film>
export function taxiQl(query: "\n\n // this query has a few empty blank lines to start\n query FindFilms2 { find { Film } }"): TypedQuery<Film>

export function taxiQl<T>(query: string): TypedQuery<T> {
   return (queries as any)[query];
}""")

       }
    }
 })

fun generatedQuery(schema: TaxiDocument, querySrc: String, resultTypeName: String): GeneratedCode {
   val querySchema = Compiler(querySrc, importSources = listOf(schema)).compile()
   val query = querySchema.queries.single()
   val snippet =  TypedQueryGenerator().generateQuerySnippet(query, resultTypeName, originalSource = null)

   return GeneratedCode(Paths.get("."), snippet.toString())
}

