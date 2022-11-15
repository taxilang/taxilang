package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.QueryMode
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContinuousQueryServicesGrammarSpec : Spek({
   describe("continuous queries") {
      describe("service grammar") {
         it("should not throw a compiler error if Stream is imported") {
            """
               import lang.taxi.Array
               import lang.taxi.Stream

               model Person {
                  foo : Array<String>
                  // This isn't really valid, but just testing compiler
                  bar : Stream<String>
               }
            """.compiled()
         }
         it("should compile a service returning a stream") {
            val doc = """
         model Person {
         }
         service StreamingService {
            operation getStreamOfPeople(): Stream<Person>
         }
      """.compiled()
            val returnType = doc.service("StreamingService")
               .operation("getStreamOfPeople")
               .returnType

            returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Stream<Person>")
            returnType.typeParameters().should.have.size(1)
            returnType.typeParameters().first().qualifiedName.should.equal("Person")
         }
      }



      describe("query grammar") {
         it("should be possible to write a query returning a stream") {
            val queries = """
               model Person {}

               stream { Person }
            """.compiledQueries()
            val query = queries.first()
            query.queryMode.should.equal(QueryMode.STREAM)
            query.typesToFind.first().typeName.parameterizedName.should.equal("lang.taxi.Stream<Person>")
         }
      }
   }

})
