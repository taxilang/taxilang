package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import lang.taxi.services.FilterCapability
import lang.taxi.services.SimpleQueryCapability

class OperationSpec : DescribeSpec({
   describe("Grammar for operations") {
      val taxi = """
         type TradeId inherits String
         type TradeDate inherits Instant
         type Trade {
            tradeId : TradeId
            tradeDate : TradeDate
         }
         type EmployeeCode inherits String
      """.trimIndent()

      it("should compile operations with array return types") {
         """
            $taxi

            service Foo {
               operation listAllTrades():Trade[]
            }
         """.trimIndent()
            .compiled().service("Foo")
            .operation("listAllTrades")
            .returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Trade>")
      }

      it("is valid to return an array using the full syntax") {
         """
            model Trade {}
            service Foo {
               operation listAllTrades():lang.taxi.Array<Trade>
            }
         """.trimIndent()
            .compiled().service("Foo")
            .operation("listAllTrades")
            .returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Trade>")
      }

      it("should parse constraints on inputs") {
         val param = """
            model Money {
               currency : CurrencySymbol inherits String
            }
            service ClientService {
              operation convertMoney(Money(this.currency == 'GBP'),target : CurrencySymbol):Money( this.currency == target )
            }
         """.trimIndent()
            .compiled().service("ClientService")
            .operation("convertMoney")
            .parameters[0]
         param.constraints.should.have.size(1)
      }

      // See OperationContextSpec ... need to pick a syntax for this
      xit("should parse constraints that reference parameters using type") {
         val param = """
         namespace demo {
            type RewardsAccountBalance {
               balance : RewardsBalance inherits Decimal
               currencyUnit : CurrencyUnit inherits String
            }
         }
         namespace test {
            service RewardsBalanceService {
               operation convert(  demo.CurrencyUnit, @RequestBody demo.RewardsAccountBalance ) : demo.RewardsAccountBalance( from source, this.currencyUnit = demo.CurrencyUnit )
            }
         }
         """.trimIndent()
            .compiled().service("ClientService")
            .operation("convertMoney")
            .parameters[0]
         param.constraints.should.have.size(1)
      }

      it("should parse constraints that are not part of return type") {
         val param = """
         namespace demo {
            type CreatedAt inherits Date
            type RewardsAccountBalance {
               balance : RewardsBalance inherits Decimal
               currencyUnit : CurrencyUnit inherits String
            }
         }
         namespace test {
            service RewardsBalanceService {
               operation findByCaskInsertedAtBetween( @PathVariable(name = "start") start : demo.CreatedAt, @PathVariable(name = "end") end : demo.CreatedAt ) : demo.RewardsAccountBalance[]( demo.CreatedAt >= start && demo.CreatedAt < end )
            }
         }
         """.trimIndent()
            .compiled().service("test.RewardsBalanceService")
            .operation("findByCaskInsertedAtBetween")
            .parameters[0]
         //    param.constraints.should.have.size(1)
      }
   }
   describe("Grammar for query operations") {
      it("should compile query grammar") {
         val queryOperation = """
            model Person {}
            type VyneQlQuery inherits String
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(==,in,like),
                  sum,
                  count
               }
            }
         """.compiled()
            .service("PersonService")
            .queryOperation("personQuery")

         queryOperation.grammar.should.equal("vyneQl")
         queryOperation.parameters.should.have.size(1)
         queryOperation.parameters[0].let {parameter ->
            parameter.name.should.equal("body")
            parameter.type.qualifiedName.should.equal("VyneQlQuery")
            parameter.annotations.should.have.size(1)
            parameter.annotations.first().name.should.equal("RequestBody")
         }
         queryOperation.returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Person>")
         val capabilities = queryOperation.capabilities
         capabilities.should.have.size(3)
         capabilities.should.contain.elements(SimpleQueryCapability.SUM, SimpleQueryCapability.COUNT)
         val filter = capabilities.filterIsInstance<FilterCapability>().first()
         filter.supportedOperations.should.have.size(3)
         filter.supportedOperations.should.contain.elements(Operator.EQUAL, Operator.IN, Operator.LIKE)
      }

      it("should generate taxi back from a compiled query operation") {
         val queryOperationTaxi = """
            model Person {}
            type VyneQlQuery inherits String
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(==,in,like),
                  sum,
                  count
               }
            }
         """.compiled()
            .service("PersonService")
            .queryOperation("personQuery")
            .asTaxi()
         val expected =
            """vyneQl query personQuery(@RequestBody body: VyneQlQuery):lang.taxi.Array<Person> with capabilities {
filter(==,in,like),
sum,
count
}"""
         queryOperationTaxi
            .withoutWhitespace()
            .should.equal(expected.withoutWhitespace())
      }

      it("can declare parameters as nullable") {
         val operation = """type PersonId inherits Int

            service PersonService {
               operation getPerson(PersonId?)
            }
         """.compiled()
            .service("PersonService")
            .operation("getPerson")


         operation.parameters[0].nullable.shouldBeTrue()
      }

      it("parameters are not nullable unless defined as such") {
         val operation = """type PersonId inherits Int

            service PersonService {
               operation getPerson(PersonId)
            }
         """.compiled()
            .service("PersonService")
            .operation("getPerson")


         operation.parameters[0].nullable.shouldBeFalse()
      }

      it("can add docs to parameters") {
         val operation = """type PersonId inherits Int

            service PersonService {
               operation getPerson(
               [[ The id of the person ]]
               id: PersonId
               )
            }
         """.compiled()
            .service("PersonService")
            .operation("getPerson")


         operation.parameters[0].typeDoc.shouldBe("The id of the person")
      }


      it("should give a compilation error for an unknown return type") {
         val errors = """
            type VyneQlQuery inherits String
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):BadType[] with capabilities {
                  filter(==,in,like),
                  sum,
                  count
               }
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal(ErrorMessages.unresolvedType("BadType"))
      }
      it("should give a compilation error for an unknown capability") {
         val errors = """
            type VyneQlQuery inherits String
            model Person {}
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(==,in,like),
                  farting,
                  count
               }
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Unable to parse 'farting' to a query capability.  Expected one of filter, sum, count, avg, min, max")
      }
      it("should give a compilation error for an unknown filter capability") {
         val errors = """
            type VyneQlQuery inherits String
            model Person {}
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(guessing),
                  count
               }
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("mismatched input 'guessing' expecting {'in', 'like', '>', '>=', '<', '<=', '==', '!='}")
      }

      it("should accept function names with findAll keyword") {
         val service = """
            model Person {}
            service PersonService {
               operation findAll(): Person[]
               operation find(): Person[]
            }
         """.compiled()
            .service("PersonService")
         service.containsOperation("findAll").shouldBeTrue()
         service.containsOperation("find").shouldBeTrue()
      }
   }

})

fun String.withoutWhitespace(): String {
   return this
      .lines()
      .map { it.trim().replace(" ","") }
      .filter { it.isNotEmpty() }
      .joinToString("")
}
