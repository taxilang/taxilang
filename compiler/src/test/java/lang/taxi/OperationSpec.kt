package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec

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
               operation findByCaskInsertedAtBetween( @PathVariable(name = "start") start : demo.CreatedAt, @PathVariable(name = "end") end : demo.CreatedAt ) : demo.RewardsAccountBalance[]( demo.CreatedAt >= start, demo.CreatedAt < end )
            }
         }
         """.trimIndent()
            .compiled().service("test.RewardsBalanceService")
            .operation("findByCaskInsertedAtBetween")
            .parameters[0]
         //    param.constraints.should.have.size(1)
      }

      it("should accept function names with findAll keyword") {
         val errors = """
            model Person {}
            service PersonService {
               operation `findAll`(): Person[]
            }
         """.validated()
         errors.should.have.size(0)
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
