package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class UnionTypesSpec : DescribeSpec({
   describe("union types") {
      // ORB-275
      it("is valid to declare the same union type in multiple places") {
       val schema =   """
         model Tweet {}
         model TweetAnalytics {}

         service Tweets {
            stream tweets : Stream<Tweet>
            stream analytics : Stream<TweetAnalytics>
         }

         // Create a query with the union type within it.
         query JoinedStreamsA {
            stream { Tweet | TweetAnalytics }
         }

         query JoinedStreamsB {
            stream { Tweet | TweetAnalytics }
         }
      """.compiled()
         val returnTypeA = schema.query("JoinedStreamsA")
            .returnType
         val returnTypeB = schema.query("JoinedStreamsA")
            .returnType

         returnTypeA.toQualifiedName().parameterizedName.shouldBe(returnTypeB.toQualifiedName().parameterizedName)
      }

      // ORB-275
      it("is valid to declare multiple fields accepting a union type") {
         val schema =   """
         model Tweet {}
         model TweetAnalytics {}

         model ThingA {
            tweetsAndAnalytics : Tweet | TweetAnalytics
         }
         model ThingB {
            moreTweetsAndAnalytics : Tweet | TweetAnalytics
         }
      """.compiled()
         val typeA = schema.objectType("ThingA").field("tweetsAndAnalytics").type
         val typeB = schema.objectType("ThingB").field("moreTweetsAndAnalytics").type
         typeA.shouldBe(typeB)
      }

      // ORB-275 - excluded, as it looks like union types are not supported on operation inputs yet.
      xit("is valid to declare multiple operations accepting a union type") {
         val f =   """
         model Tweet {}
         model TweetAnalytics {}

         service Tweets {
            operation doSomething(A|B):A|B
            operation doSomethingElse(A|B):A|B
         }
      """.compiled()
      }
   }
})
