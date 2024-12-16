package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.types.ArrayType
import lang.taxi.types.IntersectionType
import lang.taxi.types.ObjectType
import lang.taxi.types.StreamType
import lang.taxi.types.SumType
import lang.taxi.types.UnionType

class UnionTypesSpec : DescribeSpec({
   describe("union types") {
      // ORB-275
      it("is valid to declare the same union type in multiple places") {
         val schema = """
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
         val schema = """
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

      it("is possible to fetch a union type from the schema") {
         val schema = """
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
      """.compiled()

         val streamOfUnionType = schema.query("JoinedStreamsA").returnType
            .shouldBeInstanceOf<StreamType>()

         schema.type(streamOfUnionType.toQualifiedName().parameterizedName)
            .shouldNotBeNull()

         val unionType = streamOfUnionType.typeParameters()[0]
            .shouldBeInstanceOf<UnionType>()
         schema.type(unionType.toQualifiedName().parameterizedName)
            .shouldNotBeNull()
      }

      it("is possible to declare a top-level union type") {
         val taxi = """
         model Tweet {}
         model Analytics {}
         model TweetAndAnalytics = Tweet | Analytics
         """.compiled()
         val type = taxi.type("TweetAndAnalytics")
         type.shouldBeInstanceOf<ObjectType>()
         SumType.isSumTypeOrWrapper(type).shouldBe(true)
         SumType.sumTypeOrNull(type).shouldBeInstanceOf<UnionType>()
      }

      it("is invalid to declare a sum type where only one of the types is an array") {
         val errors = """
         model Tweet {}
         model Analytics {}
         model TweetAndAnalytics = Tweet[] | Analytics
         """.validated()
            .errors()

         errors.shouldHaveSize(1)
      }

      // TODO : We need to support this, as it's essentially the same as a find.
      // However, we added a rule in ADR 20240926-expression-types-may-not-return-collections.md
      // that expression types may not be collections.
      // This conflicts, so we have to decide what to do
      xit("is valid to declare a sum type where all the types are arrays") {
         val taxi = """
         model Tweet {
            id : TweetId inherits Int
         }
         model Analytics {
            id : AnalyticsId inherits Int
         }
         model TweetAndAnalytics = Tweet[] | Analytics[]
         """.compiled()
         val type = taxi.type("TweetAndAnalytics")
         type.shouldBeInstanceOf<ObjectType>()
         val arrayType = type.expression!!.returnType.shouldBeInstanceOf<ArrayType>()
         arrayType.memberType.shouldBeInstanceOf<UnionType>()

         SumType.isSumTypeOrWrapper(type).shouldBe(true)
         SumType.sumTypeOrNull(type).shouldBeInstanceOf<UnionType>()
      }
      it("is valid to reference a sum type in a query where all the types are arrays") {
         val taxi = """
         model Tweet {
            id : TweetId inherits Int
         }
         model Analytics {
            id : AnalyticsId inherits Int
         }
         query MyTestQuery {
            find { Tweet[] | Analytics[] }
         }
         """.compiled()
         val discoveryType = taxi.query("MyTestQuery")
            .discoveryType!!
         discoveryType.type.shouldBeInstanceOf<ArrayType>()
            .memberType.shouldBeInstanceOf<UnionType>()
      }

      it("is possible to declare a top-level intersection type") {
         val taxi = """
         model Tweet {}
         model Analytics {}
         model TweetAndAnalytics = Tweet & Analytics
         """.compiled()
         val type = taxi.type("TweetAndAnalytics")
         type.shouldBeInstanceOf<ObjectType>()
         SumType.isSumTypeOrWrapper(type).shouldBe(true)
         SumType.sumTypeOrNull(type).shouldBeInstanceOf<IntersectionType>()
      }

      it("is possible to declare a query-level intersection type") {
         val taxi = """
         model Tweet {}
         model Analytics {}
         query TweetAndAnalytics {
            stream { Tweet & Analytics }
         }
         """.compiled()
         val type = taxi.query("TweetAndAnalytics")
            .discoveryType!!.type.shouldBeInstanceOf<StreamType>()
            .type.shouldBeInstanceOf<IntersectionType>()
      }

      it("is possible to project an intersection type using field shorthand syntax") {
         """
            model Tweet {
               userId : UserId inherits String
               text : TweetText inherits String
            }
            model Analytics {
               viewCount : ViewCount inherits String
            }
         """.compiledWithQuery("""
            stream { Tweet & Analytics } as {
               userId,
               text,
               viewCount
            }[]
         """.trimIndent())
      }


      it("the union type has the attributes of both types") {
        val taxi = """
         model Tweet {
            id : TweetId inherits Int
            user : UserId
         }
         model User {
            userId : UserId inherits Int
         }

         // Create a query with the union type within it.
         query JoinedStreamsA {
            stream { Tweet | User }
         }
         """.compiled()
         val unionType = taxi.query("JoinedStreamsA")
            .discoveryType!!.type.shouldBeInstanceOf<StreamType>()
            .type.shouldBeInstanceOf<UnionType>()

         unionType.fields.shouldHaveSize(3)
         unionType.fields.map { it.name }
            .shouldContainExactlyInAnyOrder("id", "user", "userId")
      }
      it("renames fields where a clash exists by prefixing the type name") {
         val taxi = """
         namespace foo.test

         model Tweet {
            id : TweetId inherits Int
            user : UserId
         }
         model User {
            id : UserId inherits Int
         }

         // Create a query with the union type within it.
         query JoinedStreamsA {
            stream { Tweet | User }
         }

         type TweetAndUser = Tweet | User
         """.compiled()
         val unionType = taxi.query("foo.test.JoinedStreamsA")
            .discoveryType!!.type.shouldBeInstanceOf<StreamType>()
            .type.shouldBeInstanceOf<UnionType>()
         unionType.fields.shouldHaveSize(3)
         // Note that the id fields have been prefixed with the type names
         unionType.fields.map { it.name }.shouldContainExactlyInAnyOrder("Tweet_id", "user", "User_id")
         unionType.fields.single { it.name == "Tweet_id" }
            .type.qualifiedName.shouldBe("foo.test.TweetId")

         unionType.fields.single { it.name == "User_id" }
            .type.qualifiedName.shouldBe("foo.test.UserId")
      }

      it("renames fields where a clash exists by prefixing the fully qualified type name if the type name isnt sufficient") {
         val taxi = """
         namespace foo.test {
            model Tweet {
               id : TweetId inherits Int
               user : UserId inherits Int
            }
         }
         namespace foo.bar {
            model Tweet {
              id : TweetId inherits Int
            }
         }
         namespace foo.baz {
            // Create a query with the union type within it.
            query JoinedStreamsA {
               stream { foo.test.Tweet | foo.bar.Tweet }
            }
         }
         """.compiled()
         val unionType = taxi.query("foo.baz.JoinedStreamsA")
            .discoveryType!!.type.shouldBeInstanceOf<StreamType>()
            .type.shouldBeInstanceOf<UnionType>()
         unionType.fields.shouldHaveSize(3)
         // Note that the id fields have been prefixed with the type names
         unionType.fields.map { it.name }
            .shouldContainExactlyInAnyOrder("foo_bar_Tweet_id", "foo_test_Tweet_id", "user")
         unionType.fields.single { it.name == "foo_bar_Tweet_id" }
            .type.qualifiedName.shouldBe("foo.bar.TweetId")

         unionType.fields.single { it.name == "foo_test_Tweet_id" }
            .type.qualifiedName.shouldBe("foo.test.TweetId")
      }

      // ORB-275 - excluded, as it looks like union types are not supported on operation inputs yet.
      xit("is valid to declare multiple operations accepting a union type") {
         val f = """
         model Tweet {}
         model TweetAnalytics {}

         service Tweets {
            operation doSomething(A|B):A|B
            operation doSomethingElse(A|B):A|B
         }
      """.compiled()
      }

      it("is possible to have multiple queries with identical inline union types") {
         """
            model Tweet {}
            model Analytics {}

            query UnionQueryA {
               stream { Tweet | Analytics }
            }
            query UnionQueryB {
               stream { Tweet | Analytics }
            }
            query IntersectionQueryA {
               stream { Tweet & Analytics }
            }
            query IntersectionQueryB {
               stream { Tweet & Analytics }
            }
         """.compiledWithQuery("""
            stream { Tweet & Analytics }
         """.trimIndent())
      }
   }
})
