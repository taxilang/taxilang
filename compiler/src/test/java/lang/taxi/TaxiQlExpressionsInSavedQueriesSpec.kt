package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.LambdaExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.types.ArgumentSelector

class TaxiQlExpressionsInSavedQueriesSpec : DescribeSpec({

   describe("Using expressions within given clauses that reference variables from saved query") {
      it("is possible to use variable from query in given clause") {
         val (schema, query) = """
         type HumanId inherits String
         model Person {
            personId : PersonId inherits String
         }
      """.compiledWithQuery(
            """
         query MyQuery(humanId : HumanId) {
            given { personId : PersonId = (PersonId) humanId }
            find { Person }
         }
      """.trimIndent()
         )
      }
      it("is possible to use variable from query in expression") {
         val (schema, query) = """
    model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }
            model Cast {
               actors : Actor[]
            }
            model Actor {
               name : PersonName inherits String
            }
""".compiledWithQuery("""
   query FindSomeFilms( starring : PersonName ) {
      find { Film[] } as (filter(Actor[], (PersonName) -> PersonName == starring)) -> {
      }[]
   }
""".trimIndent())
         query.shouldNotBeNull()
         query.projectionScopeVars.single()
            .expression!!
            .shouldBeInstanceOf<FunctionExpression>()
            .function
            .inputs[1]
            .shouldBeInstanceOf<LambdaExpression>()
            .expression
            .shouldBeInstanceOf<OperatorExpression>()
            .rhs
            .shouldBeInstanceOf<ArgumentSelector>()
            .scope.name.shouldBe("starring")
      }
   }

   it("is possible to use variable from query in a field projection expression") {
      val (schema, query) = """
    model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }
            model Cast {
               actors : Actor[]
            }
            model Actor {
               name : PersonName inherits String
            }
""".compiledWithQuery("""
   query FindSomeFilms( starring : PersonName ) {
      find { Film[] } as {
         starring : (filter(Actor[], (PersonName) -> PersonName == starring))
      }[]
   }
""".trimIndent())
      query.shouldNotBeNull()

      val starringField = query.projectedObjectType!!
         .field("starring")

      starringField.accessor
         .shouldBeInstanceOf<FunctionExpression>()
         .function
         .inputs[1]
         .shouldBeInstanceOf<LambdaExpression>()
         .expression
         .shouldBeInstanceOf<OperatorExpression>()
         .rhs
         .shouldBeInstanceOf<ArgumentSelector>()
         .scope.name.shouldBe("starring")
   }

})
