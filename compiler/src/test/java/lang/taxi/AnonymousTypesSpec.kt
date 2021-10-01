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
               firstName : FirstName inherits String
               lastName : LastName inherits String
               education: {
                   highSchoolName: SchoolName inherits String
                   undergraduate: UniversityName inherits String
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
      it("is possible to declare nested anonymous types") {
         val model = """
model LeiRecord {
    data: {
        @Id
        id: Lei inherits String
        attributes: {
            entity: {
                legalName: {
                    name: CompanyName inherits String
                }
            }
        }
    }
}""".compiled()
            .model("LeiRecord")
         model.fields.should.have.size(1)
         model.field("data").type.asA<ObjectType>().fields.should.have.size(2)
      }
   }
})
