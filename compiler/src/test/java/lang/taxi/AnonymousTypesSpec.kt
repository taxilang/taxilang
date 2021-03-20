package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.ObjectType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AnonymousTypesSpec : Spek({
   describe("Anonymous Types") {
      it("simple anonymous types") {
         val src = """
            model Person {
               firstName : FirstName as String
               lastName : LastName as String
               education: {
                   highSchoolName: SchoolName as String
                   undergraduate: UniversityName as String
               }
            }
           """.trimIndent()
         val person = Compiler(src).compile().model("Person")
         person.should.not.be.`null`
         person.hasField("firstName").should.be.`true`
         person.hasField("lastName").should.be.`true`
         person.hasField("education").should.be.`true`
         val fieldWithAnonymousType = person.field("education").type as ObjectType
         fieldWithAnonymousType.anonymous.should.be.`true`
         fieldWithAnonymousType.qualifiedName.should.equal("Person\$Education")
         fieldWithAnonymousType.hasField("highSchoolName").should.be.`true`
         fieldWithAnonymousType.hasField("undergraduate").should.be.`true`
      }
   }
})
