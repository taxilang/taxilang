package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.types.ObjectType
import lang.taxi.types.ScopedReferenceSelector

class TaxiQlNamedProjectionScopeSpec : DescribeSpec({
   describe("named projection scopes") {
      it("should allow definition of a named projection scope") {
         val (schema,query) = """model Actor {
              actorId : ActorId inherits Int
              name : ActorName inherits String
            }
            model Film {
               title : FilmTitle inherits String
               headliner : ActorId
               cast: Actor[]
            }
         """.compiledWithQuery("""
            find { Film[] } as (film:Film) -> {
               title : FilmTitle
               star : singleBy(film.cast, (Actor) -> Actor::ActorId, film.headliner) as (actor:Actor) -> {
                  name : actor.name
                  title : film.title
               }
            }[]
         """.trimIndent())
         query.projectionScope!!.name.shouldBe("film")
         query.projectionScope!!.type.qualifiedName.shouldBe("Film")
         val projection = query.projectedObjectType.field("star").projection
         projection!!.projectionFunctionScope.name.should.equal("actor")
         val projectedType = projection.projectedType.asA<ObjectType>()
         val accessor = projectedType.field("name").accessor!!.asA<ScopedReferenceSelector>()
         accessor.scope.name.should.equal("actor")
         accessor.scope.type.qualifiedName.should.equal("Actor")

      }
   }
})
