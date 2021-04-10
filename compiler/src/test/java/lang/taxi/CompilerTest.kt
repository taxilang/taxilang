package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.ObjectType
import org.antlr.v4.runtime.CharStreams
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe


class CompilerTest: Spek({
   describe("compilation") {
      it("should infer the return type from the field") {
         val schema = """
         model OrderView {
               orderId : String?
            }
      """.trimIndent()

         val taxi = Compiler(schema).compile()
         val initialOrderView = taxi.type("OrderView") as ObjectType
         initialOrderView.fields.size.should.equal(1)

         val source = """
            model OrderView {
               orderId : String?
               orderEntry: String
            }
         """.trimIndent()

        val updatedTaxi = Compiler(CharStreams.fromString(source, Compiler.UNKNOWN_SOURCE), listOf(taxi)).compile()
         val updatedOrderView = updatedTaxi.type("OrderView") as ObjectType
         updatedOrderView.fields.size.should.equal(2)
      }
   }
})
