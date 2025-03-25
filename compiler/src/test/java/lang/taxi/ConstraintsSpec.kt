package lang.taxi

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.MemberAccessExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.expressions.TypeExpression
import lang.taxi.query.convertToConstraint
import lang.taxi.services.operations.constraints.ExpressionConstraint
import lang.taxi.types.MemberTypeReferenceExpression
import lang.taxi.types.ObjectType

class ConstraintsSpec : DescribeSpec({
   describe("Constraints") {
      it("can declare constraints on types") {
         val source = """
type Money {
   amount : Amount inherits Decimal
   currency : Currency inherits String
}
type SomeServiceRequest {
   amount : Money(Currency == 'GBP')
   clientId : ClientId inherits String
}
"""
         val doc = Compiler(source).compile()
         val request = doc.objectType("SomeServiceRequest")

         val amountField = request.field("amount")
         expect(amountField.constraints).to.have.size(1)
         val constraint = amountField.constraints.single() as ExpressionConstraint
         val expression = constraint.expression as OperatorExpression

         expression.lhs.asA<TypeExpression>().type.qualifiedName.shouldBe("Currency")
         expression.rhs.asA<LiteralExpression>().value.shouldBe("GBP")
      }
   }

   it("can downgrade expression constraints") {
      val (schema,query) = """
         model Person {
            name : Name inherits String
            lastName: LastName inherits String
            age: Age inherits Int
         }""".compiledWithQuery("find { Person[]( Name == 'Jimmy' && LastName == 'Page' || Age == 99) }")
      val constraint = query.typesToFind.single().constraints.single() as ExpressionConstraint
      val converted = constraint.convertToConstraint()
      converted.size.should.equal(5)
   }

   // ORB-766
   it("does not parse a function call as a constraint") {
      val (schema,query) = """
         closed model Person {
           name : PersonName inherits String
          }
          model Movie {
            cast : Person[]
         }
      """.compiledWithQuery("""
         find { Movie[] } as {
         // This isn't a constraint, it's a function call
         // but it could be parsed either way
             starring : first(Person[]) as {
               starsName : PersonName
            }
         }[]
      """.trimIndent())
      query.projectedObjectType!!
         .field("starring")
         .constraints.shouldBeEmpty()
   }

   it("parses constraints of nested properties") {
      val (schema,query) = """
   closed model Deal {
     id : DealId inherits Int
     borrowerId : BorrowerId inherits Int
   }
   closed model ExistingDeals {
     deals: Deal[]
   }

   service DealApi {
     operation getDeal(DealId):Deal(...)
     operation getExistingDeals(BorrowerId):ExistingDeals(...)
   }
""".compiledWithQuery("""
given { id: DealId = 1}
find { Deal(DealId == id )} as(deal: Deal) -> {
    existing : ExistingDeals(BorrowerId == deal.borrowerId)
    ...
}
""".trimIndent())
      query.projectedObjectType!!
         .field("existing")
   }

   it("compiles constraints on field of anonymous field type") {
      val (schema, query) = """
      model Film {
         id : FilmId inherits Int
      }
      model FilmRevenue {}
      """.trimIndent().compiledWithQuery("""
         find { Film[] } as (film:Film) -> {
            earnings: {
               revenue: FilmRevenue(FilmId == film.id)
            }
         }[]
      """.trimIndent())
      val revenueField = query.projectedObjectType!!.field("earnings").type.asA<ObjectType>().field("revenue")
      revenueField.constraints.shouldHaveSize(1)
   }
   it("parses constraints of nested properties with projections") {
val (schema,query) = """
   closed model Deal {
     id : DealId inherits Int
     borrowerId : BorrowerId inherits Int
   }
   closed model ExistingDeals {
     deals: Deal[]
   }
""".compiledWithQuery("""
given { id: DealId = 1}
find { Deal(DealId == id )} as(deal: Deal) -> {
    existing : Deal[] = ExistingDeals(BorrowerId == deal.borrowerId) as Deal[]
//    existing : ExistingDeals(BorrowerId == deal.borrowerId)
    ...
}
""".trimIndent())
      val field = query.projectedObjectType!!
         .field("existing")
      field.projection!!
         .sourceTypeConstraints.shouldHaveSize(1)
   }

   it("can use a field member accessor on the rhs of a constraints expression") {
      val (schema,query) = """
         model Film {
            filmId : FilmId inherits Int
         }
         model FilmReview {
            reviewId : ReviewId inherits String
         }
         model ReviewWrapper {
            reviewId: ReviewId
            review: FilmReview
         }
      """.compiledWithQuery("""
         find { Film[] } as (film:Film) -> {
           review: FilmReview(ReviewId == ReviewWrapper(FilmId == film::FilmId).reviewId)
         }[]
      """.trimIndent())
      val reviewField = query.projectedObjectType!!.field("review")
      val expression = reviewField.constraints.single()
         .shouldBeInstanceOf<ExpressionConstraint>()
         .expression.shouldBeInstanceOf<OperatorExpression>()
      expression.lhs.shouldBeInstanceOf<TypeExpression>()
         .type.qualifiedName.shouldBe("ReviewId")

      val memberAccessExpression = expression.rhs.shouldBeInstanceOf<MemberAccessExpression>()
      val memberTypeExpression = memberAccessExpression.lhs.shouldBeInstanceOf<TypeExpression>()
      memberTypeExpression.type.qualifiedName.shouldBe("ReviewWrapper")
      memberTypeExpression.constraints.single()
         .shouldBeInstanceOf<ExpressionConstraint>()
         .expression.shouldBeInstanceOf<OperatorExpression>()

      memberAccessExpression.rhs.fieldName.shouldBe("reviewId")
   }

   it("can use a type member accessor on the rhs of a constraints expression") {
      val (schema,query) = """
         model Film {
            filmId : FilmId inherits Int
         }
         model FilmReview {
            reviewId : ReviewId inherits String
         }
         model ReviewWrapper {
            review: FilmReview
         }
      """.compiledWithQuery("""
         find { Film[] } as (film:Film) -> {
           review: FilmReview(ReviewId == ReviewWrapper(FilmId == film::FilmId)::ReviewId)
         }[]
      """.trimIndent())
      val reviewField = query.projectedObjectType!!.field("review")
      val expression = reviewField.constraints.single()
         .shouldBeInstanceOf<ExpressionConstraint>()
         .expression.shouldBeInstanceOf<OperatorExpression>()
      expression.lhs.shouldBeInstanceOf<TypeExpression>()
         .type.qualifiedName.shouldBe("ReviewId")

      val memberAccessExpression = expression.rhs.shouldBeInstanceOf<MemberTypeReferenceExpression>()
      memberAccessExpression.returnType.qualifiedName.shouldBe("ReviewId")
      memberAccessExpression.targetType.qualifiedName.shouldBe("ReviewId")
      memberAccessExpression.memberSource.parameterizedName.shouldBe("ReviewWrapper")
      val memberTypeExpression = memberAccessExpression.sourceExpression.shouldBeInstanceOf<TypeExpression>()
      memberTypeExpression.type.qualifiedName.shouldBe("ReviewWrapper")
      memberTypeExpression.constraints.single()
         .shouldBeInstanceOf<ExpressionConstraint>()
         .expression.shouldBeInstanceOf<OperatorExpression>()
   }
})
