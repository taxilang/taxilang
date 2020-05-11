package lang.taxi

import com.winterbe.expekt.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ModelSpec : Spek({
   describe("model syntax") {
      describe("simple grammar") {
         it("should allow declaration of a model") {
            val src = """
model Person {
   firstName : FirstName as String
   lastName : LastName as String
}
           """.trimIndent()
            val person = Compiler(src).compile().model("Person")
            person.should.not.be.`null`
            person.hasField("firstName").should.be.`true`
            person.hasField("lastName").should.be.`true`
         }
      }
   }
})
