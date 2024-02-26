package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import lang.taxi.expressions.FunctionExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.ArgumentSelector
import lang.taxi.types.ObjectType

class TaxiQlNamedProjectionScopeSpec : DescribeSpec({
   describe("named projection scopes") {
      it("should allow definition of a named projection scope") {
         val (schema, query) = """model Actor {
              actorId : ActorId inherits Int
              name : ActorName inherits String
            }
            model Film {
               title : FilmTitle inherits String
               headliner : ActorId
               cast: Actor[]
            }
         """.compiledWithQuery(
            """
            find { Film[] } as (film:Film) -> {
               title : FilmTitle
               star : singleBy(film.cast, (Actor) -> Actor::ActorId, film.headliner) as (actor:Actor) -> {
                  name : actor.name
                  title : film.title
               }
            }[]
         """.trimIndent()
         )
         query.projectionScopeVars.single().name.shouldBe("film")
         query.projectionScopeVars.single().type.qualifiedName.shouldBe("Film")
         val projection = query.projectedObjectType!!.field("star").projection
         projection!!.projectionFunctionScope.single().name.should.equal("actor")
         val projectedType = projection.projectedType.asA<ObjectType>()
         val accessor = projectedType.field("name").accessor!!.asA<ArgumentSelector>()
         accessor.scope.name.should.equal("actor")
         accessor.scope.type.qualifiedName.should.equal("Actor")
      }

      it("should allow referencing a named projection scope in a constraint") {
         val (schema, query) = """
            model Film {
               id : FilmId inherits String
            }
            model Review {
               id : ReviewId inherits String
               film : FilmId
            }
         """.compiledWithQuery(
            """
            find { Film[] } as (src:Film) -> {
               film : Film
               review : Review( FilmId == src.id)
            }[]
         """.trimIndent()
         )
         query.shouldNotBeNull()
         val reviewConstraints = query.projectedObjectType!!.field("review").constraints
         reviewConstraints.shouldHaveSize(1)
         val constraint = reviewConstraints.single() as ExpressionConstraint
         val expression = constraint.expression as OperatorExpression
         val selector = expression.rhs.asA<ArgumentSelector>()
         selector.scope.name.shouldBe("src")
         selector.scope.type.qualifiedName.shouldBe("Film")
      }

      it("is possible to define an expression as a named input with implied type to a scope") {
         val (schema, query) = """
            model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }
            model Actor {
               name : ActorName inherits String
            }
         """.compiledWithQuery(
            """
            find { Film(FilmId == 1) } as (lead: first(Actor[])) -> {
               title : Title
               starring : lead.name
            }
         """.trimIndent()
         )
         query.shouldNotBeNull()
         query.projectionScopeVars.single().name.shouldBe("lead")
         query.projectionScopeVars.single().type.qualifiedName.shouldBe("Actor")
         query.projectionScopeVars.single().expression!!.asA<FunctionExpression>().function.qualifiedName.shouldBe("taxi.stdlib.first")
      }
      it("is possible to define an expression as a named input with explicit type to a scope") {
         val (schema, query) = """
            model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }
            model Actor {
               name : ActorName inherits String
            }
         """.compiledWithQuery(
            """
            find { Film(FilmId == 1) } as (lead: Actor = first(Actor[])) -> {
               title : Title
               starring : lead.name
            }[]
         """.trimIndent()
         )
         query.shouldNotBeNull()
         query.projectionScopeVars.single().name.shouldBe("lead")
         query.projectionScopeVars.single().type.qualifiedName.shouldBe("Actor")
         query.projectionScopeVars.single().expression!!.asA<FunctionExpression>().function.qualifiedName.shouldBe("taxi.stdlib.first")
      }

      it("is possible to define an expression as an unnamed input to a scope") {
         val (schema, query) = """
            model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }
            model Actor {
               name : ActorName inherits String
            }
         """.compiledWithQuery(
            """
            find { Film(FilmId == 1) } as (first(Actor[])) -> {
               title : Title
               starring : ActorName
            }[]
         """.trimIndent()
         )
         query.shouldNotBeNull()
         query.projectionScopeVars.single().type.qualifiedName.shouldBe("Actor")
         query.projectionScopeVars.single().expression!!.asA<FunctionExpression>().function.qualifiedName.shouldBe("taxi.stdlib.first")
      }


      it("is possible to define multiple variables as inputs to scopes") {
         val (schema, query) = """
            model Film {
               id : FilmId inherits Int
               title : Title inherits String
            }
            model Actor {
               name : ActorName inherits String
            }
         """.compiledWithQuery(
            """
            find { Film(FilmId == 1) } as (Film, first(Actor[])) -> {
               title : Title
               starring : ActorName
            }[]
         """.trimIndent()
         )
         query.shouldNotBeNull()
         query.projectionScopeVars.shouldHaveSize(2)

         query.projectionScopeVars[0].type.qualifiedName.shouldBe("Film")
         query.projectionScopeVars[1].type.qualifiedName.shouldBe("Actor")
         query.projectionScopeVars[1].expression!!.asA<FunctionExpression>().function.qualifiedName.shouldBe("taxi.stdlib.first")
      }


      // This ins't implemented, but it should be.
      xit("should allow referencing a named projection scope in a constraint using a type selector") {
         val (schema, query) = """
            model Film {
               id : FilmId inherits String
            }
            model Review {
               id : ReviewId inherits String
               film : FilmId
            }
         """.compiledWithQuery(
            """
            find { Film[] } as (src:Film) -> {
               film : Film
               review : Review( FilmId == src::FilmId)
            }[]
         """.trimIndent()
         )
         query.shouldNotBeNull()
         val reviewConstraints = query.projectedObjectType!!.field("review").constraints
         reviewConstraints.shouldHaveSize(1)
         val constraint = reviewConstraints.single() as OperatorExpression
         val selector = constraint.rhs.asA<ArgumentSelector>()
         selector.scope.name.shouldBe("src")
         selector.scope.type.qualifiedName.shouldBe("Film")
      }
   }
})
