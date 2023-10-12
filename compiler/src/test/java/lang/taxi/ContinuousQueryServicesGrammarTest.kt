package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.query.QueryMode
import lang.taxi.types.StreamType
import lang.taxi.types.UnionType

class ContinuousQueryServicesGrammarSpec : DescribeSpec({
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

         it("is possible to request multiple types when writing a stream query") {
            val schema = """
               model Tweet {
                  id : TweetId inherits String
                  text: TweetText inherits String
               }
               model Analytics {
                  id : TweetId
                  viewCount : ViewCount inherits Int
               }

               stream { Tweet | Analytics } as {
                  tweetId : TweetId
                  text : TweetText
                  views : ViewCount
               }[]
            """.compiled()
            val streamingType = schema.queries.single().typesToFind.single().type
            streamingType.shouldBeInstanceOf<StreamType>()
            val unionType = streamingType.typeParameters()[0]
            unionType.shouldBeInstanceOf<UnionType>()
            unionType.types.shouldHaveSize(2)
            schema.containsType(unionType.qualifiedName).shouldBeTrue()
         }
      }
   }

})
