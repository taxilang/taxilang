package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.types.ArrayType
import lang.taxi.types.ObjectType

// These are exploratory tests
class ChainedProjectionsSpec : DescribeSpec({
   describe("chaining projection types") {
      val schema = """
         model Studio {}
         model Film {
            id : FilmId inherits Int
         }

         model Actor {
            name : PersonName inherits String
         }
         model CastResponse {
            actors : Actor[]
         }
      """
      it("chains projections on a field") {
         val (_, query) = schema.compiledWithQuery(
            """
find { Film } as {
   id : FilmId
   cast : CastResponse as Actor[] as (actor:Actor) -> {
      personName : PersonName
   }[]
}
      """.trimIndent()
         )
         val field = query.projectedObjectType!!.field("cast")
         field.projection
      }

      it("chains projections on a field in a nested anonymous type") {
         val (_, query) = schema.compiledWithQuery(
            """
find { Studio } as {
   film : Film as {
      id : FilmId
      cast : CastResponse as Actor[] as (actor:Actor) -> {
         personName : PersonName
      }[]
   }
}
      """.trimIndent()
         )
         val castField = query.projectedObjectType!!.field("film")
            .type.asA<ObjectType>()
            .field("cast")

         val castArrayType = castField.type.shouldBeInstanceOf<ArrayType>()
         castArrayType.memberType
            .asA<ObjectType>()
            .hasField("personName")
            .shouldBeTrue()
      }

      it("chains projections on a field without scoped vars") {
         val (_, query) = schema.compiledWithQuery(
            """
find { Film } as {
   id : FilmId
   cast : CastResponse as Actor[]
}
      """.trimIndent()
         )
         val field = query.projectedObjectType!!.field("cast")
         field.projection
      }

      it("single projection on a field without scoped vars") {
         val (_, query) = schema.compiledWithQuery(
            """
find { Film } as {
   id : FilmId
   cast : Actor[] as {
      actorName : PersonName
   }[]
}
      """.trimIndent()
         )
         val field = query.projectedObjectType!!.field("cast")
         field.projection
      }

      it("single projection on a field with scoped vars") {
         val (_, query) = schema.compiledWithQuery(
            """
find { Film } as {
   id : FilmId
   cast : Actor[] as (actor:Actor) -> {
      actorName : PersonName
   }[]
}
      """.trimIndent()
         )
         val field = query.projectedObjectType!!.field("cast")
         field.projection
      }

      it("single top-level projection") {
         val (_, query) = schema.compiledWithQuery(
            """
find { Film } as Actor[]
      """.trimIndent()
         )
         val field = query
      }

      it("top-level chained projection") {
         val (_,query) = schema.compiledWithQuery(
            """find { Film } as {
   id : FilmId
   cast : CastResponse as Actor[] as (actor:Actor) -> {
      personName : PersonName
   }[]
}"""
         )
         val field = query
      }
   }
})
