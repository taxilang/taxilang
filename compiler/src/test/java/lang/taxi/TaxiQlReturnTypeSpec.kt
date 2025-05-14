package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import lang.taxi.query.TaxiQlQuery
import lang.taxi.types.Type

class TaxiQlReturnTypeSpec :  DescribeSpec({
   describe("detecting the return type of a taxiQL query") {
      val schema = """
         model Person {
            name : FirstName inherits String
         }
         model AccountBalance {
            balance : Money inherits Int
         }
         model UpdateResult {
            ok : Boolean
         }
         service PersonApi {
            write operation clearAll():UpdateResult
            write operation bulkUpdate(Person[]):UpdateResult[]
            write operation updateOne(Person):UpdateResult
         }

      """
      it("the return type of a find {} is the type in the find") {
         schema.compiledWithQuery("""
            find { Person }
         """.trimIndent())
            .shouldHaveReturnTypeNamed("Person")
      }
      it("the return type of a find-and-project is the type in the projection if anonymous") {
         schema.compiledWithQuery("""
            find { Person } as {
               firstName : FirstName
            }
         """.trimIndent())
            .queryReturnType().anonymous.shouldBeTrue()
      }
      it("the return type of a find-and-project is the type in the projection if named type") {
         schema.compiledWithQuery("""
            find { Person } as AccountBalance
         """.trimIndent())
            .shouldHaveReturnTypeNamed("AccountBalance")
      }
      it("the return type of find-array-and-project-to-array is array of the projection type") {
         schema.compiledWithQuery("""find { Person[] } as AccountBalance[]""")
            .shouldHaveReturnTypeNamed("lang.taxi.Array<AccountBalance>")
      }
      it("the return type of find-array-and-project-to-single is single of the projection type") {
         schema.compiledWithQuery("""find { Person[] } as AccountBalance""")
            .shouldHaveReturnTypeNamed("AccountBalance")
      }
      it("the return type of a stream is a stream") {
         schema.compiledWithQuery("""
            stream { Person }
         """.trimIndent())
            .shouldHaveReturnTypeNamed("lang.taxi.Stream<Person>")
      }
      it("the return type of a stream is a stream when projecting to anonymous type") {
         val returnType = schema.compiledWithQuery("""
            stream { Person } as {
               firstName : FirstName
            }[]
         """.trimIndent())
            .queryReturnType()
         returnType.toQualifiedName().parameterizedName.shouldStartWith("lang.taxi.Stream<")
         returnType.typeParameters().single().anonymous.shouldBeTrue()
      }
      it("the return type of a stream is a stream when projecting to named type") {
         val returnType = schema.compiledWithQuery("""
            stream { Person } as AccountBalance[]
         """.trimIndent())
            .queryReturnType()
         returnType.toQualifiedName().parameterizedName.shouldStartWith("lang.taxi.Stream<")
         returnType.typeParameters().single().qualifiedName.shouldBe("AccountBalance")
      }
      it("the return type of a mutation is the response of the mutation") {
         schema.compiledWithQuery("""
            call PersonApi::clearAll
         """.trimIndent())
            .shouldHaveReturnTypeNamed("UpdateResult")
      }
      it("the return type of a find single-then-mutate is the response of the mutation") {
         schema.compiledWithQuery("""
            find { Person }
            call PersonApi::updateOne
         """.trimIndent())
            .shouldHaveReturnTypeNamed("UpdateResult")
      }
      it("the return type of a find single-then-project-then-mutate is the response of the mutation") {
         schema.compiledWithQuery("""
            find { Person } as AccountBalance
            call PersonApi::updateOne
         """.trimIndent())
            .shouldHaveReturnTypeNamed("UpdateResult")
      }
      it("the return type of a find single-then-project-to-array-then-mutate is the response of the mutation") {
         schema.compiledWithQuery("""
            find { Person } as AccountBalance[]
            call PersonApi::bulkUpdate
         """.trimIndent())
            .shouldHaveReturnTypeNamed("lang.taxi.Array<UpdateResult>")
      }
     it("the return type of a map-then-mutate is an array of the response of the mutation") {
        schema.compiledWithQuery("""
           map { Person[] } // For each of this...
           call PersonApi::updateOne // call this
        """.trimIndent())
           .shouldHaveReturnTypeNamed("lang.taxi.Array<UpdateResult>")
     }


   }
})

private fun Pair<TaxiDocument, TaxiQlQuery>.shouldHaveReturnTypeNamed(string: String) {
   this.queryReturnType().toQualifiedName().parameterizedName.shouldBe(string)
}

private fun Pair<TaxiDocument, TaxiQlQuery>.queryReturnType(): Type {
   return this.second.returnType
}
