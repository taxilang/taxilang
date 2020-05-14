package lang.taxi

import com.winterbe.expekt.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OperationSpec : Spek({
   describe("Grammar for operations") {
      val taxi = """
         type Trade {
            tradeId : TradeId as String
            tradeDate : TradeDate as Instant
         }
         type alias EmployeeCode as String
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
   }
})
