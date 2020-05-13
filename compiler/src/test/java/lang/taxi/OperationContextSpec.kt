package lang.taxi

import com.winterbe.expekt.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OperationContextSpec : Spek({
   describe("declaring context to operations") {
      val taxi = """
         type Trade {
            tradeId : TradeId as String
            tradeDate : TradeDate as Instant
         }
      """.trimIndent()

      describe("compiling declaration of context") {
         it("compiles return type with property type equal to param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTrade(id:TradeId):Trade(
               TradeId = id
            )
         }
         """.compiled().service("TradeService").operation("getTrade")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            TODO("Validate")
         }

         it("compiles return type with property field equal to param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTrade(tradeId:TradeId):Trade[](
               this.tradeId = tradeId
            )
         }
         """.compiled().service("TradeService").operation("getTrade")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            TODO("Validate")
         }


         it("compiles return type with property type greater than param") {
            val operation = """
         $taxi
         service TradeService {
            operation getTradesAfter(startDate:Instant):Trade[](
               TradeDate >= startDate
            )
         }
         """.compiled().service("TradeService").operation("getTradesAfter")
            operation.contract!!.returnTypeConstraints.should.have.size(1)
            TODO("Validate")
         }

         it("compiles return type with multiple property params") {
            val operation = """
         $taxi
         service TradeService {
            operation getTradesAfter(startDate:Instant, endDate:Instant):Trade[](
               TradeDate >= startDate,
               TradeDate < endDate
            )
         }
         """.compiled().service("TradeService").operation("getTradesAfter")
            operation.contract!!.returnTypeConstraints.should.have.size(2)
            TODO("Validate")
         }
      }


   }
})

fun String.compiled(): TaxiDocument {
   return Compiler(this).compile()
}

