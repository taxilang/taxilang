package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
         query.projectionScope!!.name.shouldBe("film")
         query.projectionScope!!.type.qualifiedName.shouldBe("Film")
         val projection = query.projectedObjectType!!.field("star").projection
         projection!!.projectionFunctionScope.name.should.equal("actor")
         val projectedType = projection.projectedType.asA<ObjectType>()
         val accessor = projectedType.field("name").accessor!!.asA<ArgumentSelector>()
         accessor.scope.name.should.equal("actor")
         accessor.scope.type.qualifiedName.should.equal("Actor")
      }

      it("should not allow narrowing projection scope to a type that is not present") {
         val complilationError = """
            type Unrelated inherits String
            model Actor {
              actorId : ActorId inherits Int
              name : ActorName inherits String
            }
            model Film {
               title : FilmTitle inherits String
               headliner : ActorId
               cast: Actor[]
            }
         """.compiledWithQueryProducingCompilationException(
            """
            find { Film[] } as (unrelated:Unrelated) -> {
               title : FilmTitle
            }[]
         """.trimIndent()
         )
         TODO()
      }
      it("should not allow narrowing a projection to a type that is present within an array") {
         val complilationError = """
            type ActorName inherits String
            model Movie {
                title : MovieTitle inherits String
                actors : ActorName[]
            }
         """.compiledWithQueryProducingCompilationException(
            """
                // This should be illegal, as ActorName is actually present in an Array,
                // (ActorName[])
                // so the projection scope is ambiguous.
                //
            find { Movie } as (actors:ActorName) -> {
               actorNames : ActorName
            }
         """.trimIndent()
         )
         TODO()
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
