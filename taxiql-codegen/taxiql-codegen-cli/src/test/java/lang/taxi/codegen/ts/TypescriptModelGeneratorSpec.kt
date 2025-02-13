package lang.taxi.codegen.ts

import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import lang.taxi.codegen.GeneratedCode
import lang.taxi.compiled
import lang.taxi.types.QualifiedName

class TypescriptModelGeneratorSpec : DescribeSpec({

   describe("Generating typescript models from Taxi") {

      it("should handle type name collisions") {
         uniqueNames(listOf("com.foo.Film", "com.foo.Movie", "com.foo.Person"))
            .shouldContainExactly("Film", "Movie", "Person")

         uniqueNames(listOf("com.foo.Film", "com.bar.Film", "com.foo.Person"))
            .shouldContainExactly("foo_Film", "bar_Film", "Person")

         uniqueNames(listOf("org.foo.Film", "com.foo.Film", "com.foo.Person"))
            .shouldContainExactly("org_foo_Film", "com_foo_Film", "Person")
      }
      it("should generate a simple model") {
         generatedTypescript("Film", """
            model Film {
               id : FilmId inherits Int
               name : FilmTitle inherits String
            }
         """)
            .shouldGenerate("""
export interface Film {
  id: FilmId
  name: FilmTitle
}
export type FilmId = number;
export type FilmTitle = string;
""".trimIndent())
      }
      it("should generate a simple model where the provided type name is an array") {
         generatedTypescript("Film[]", """
            model Film {
               id : FilmId inherits Int
               name : FilmTitle inherits String
            }
         """)
            .shouldGenerate("""
export interface Film {
  id: FilmId
  name: FilmTitle
}
export type FilmId = number;
export type FilmTitle = string;
""".trimIndent())
      }

      it("should generate a model with self-referential types") {
         generatedTypescript("Film", """
model Film {
   id: FilmId inherits Int
   name: FilmTitle inherits String
   sequels: Film[]
}
         """.trimIndent())
            .shouldGenerate("""
export interface Film {
  id: FilmId
  name: FilmTitle
  sequels: Film[]
}
export type FilmId = number;
export type FilmTitle = string;
            """.trimIndent())
      }
      it("should generate a model with enums") {
         generatedTypescript("Film", """
enum Rating {
   G,
   PG
}
model Film {
   id: FilmId inherits Int
   name: FilmTitle inherits String
   rating : Rating
}
         """.trimIndent())
            .shouldGenerate("""
export interface Film {
  id: FilmId
  name: FilmTitle
  rating: Rating
}
export type FilmId = number;
export type FilmTitle = string;
export type Rating = 'G' | 'PG';
            """.trimIndent())
      }

      it("should generate inner types") {
         generatedTypescript("Film", """
model Film {
   id: FilmId inherits Int
   director: {
      name : PersonName inherits String
   }
}
         """.trimIndent())
            .shouldGenerate("""
export interface Film {
  id: FilmId
  director: {
     name: PersonName
   }
}
export type FilmId = number;
export type PersonName = string;
            """.trimIndent())
      }
      it("should generate arrays of inner types") {
         generatedTypescript("Film", """
model Film {
   id: FilmId inherits Int
   director: {
      name : PersonName inherits String
   }[]
}
         """.trimIndent())
            .shouldGenerate("""
export interface Film {
  id: FilmId
  director: {
     name: PersonName
   }[]
}
export type FilmId = number;
export type PersonName = string;
            """.trimIndent())
      }
   }

   xit("can generate typescript renaming the type of an anonymous type") {
      val schema = """
         model Film {
            title : Title inherits String
         }
         query FindFilms {
            find { Film[] } as {
               name : Title
            }[]
         }
      """.compiled()
      val query = schema.query("FindFilms")
      TypescriptModelGenerator(query.returnType,
         nameSubstitutions = mapOf(
            query.returnType.toQualifiedName() to QualifiedName.from("FindFilmsResult")
         )
      )
         .build()
         .second
         .shouldGenerate("""
export interface FindFilmsResult {
  name: Title
}
""".trimIndent())
   }
})

fun generatedTypescript(typeName: String, taxi: String): GeneratedCode {
   val schema = taxi.compiled()
   return TypescriptModelGenerator(schema.type(typeName))
      .build()
      .second
}

private fun Iterable<String>.fqn(): Set<QualifiedName> {
   return this.map { QualifiedName.from(it) }.toSet()
}

private fun uniqueNames(names: List<String>): Set<String> {
   return TypescriptModelGenerator.getUniqueNames(names.fqn())
      .values.toSet()
}

fun GeneratedCode.shouldGenerate(expected: String) {
   val expectedLines = expected.lines()
   val actualContent = this.content
   val actualLines = this.content.lines()
   withClue("Generated ${actualLines.size} lines, but only expected ${expectedLines.size} lines.") {
      if (actualLines.size != expectedLines.size) {
         // FAil, but with a helpful message
         actualContent shouldBe expected
      }

   }
   val incorrectLines = actualContent
      .lines()
      .mapIndexedNotNull { index, actual ->
         if (actual.trim() == expectedLines[index].trim()) {
            null
         } else {
            index to actual
         }
      }
   if (incorrectLines.isNotEmpty()) {
      val incorrectLine = incorrectLines.first()
         "Difference at line ${incorrectLine.first} of generated code \n$this".asClue {
         incorrectLine.second.shouldBe(expectedLines[incorrectLine.first])
      }
   }
}
