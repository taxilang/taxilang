package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import lang.taxi.types.PrimitiveType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AssignmentSpec : DescribeSpec({
   describe("Type assignment rules") {
      val schema = """
      type EyeColour inherits String
      type Name inherits String
      type alias Identifier as Name

      type FirstName inherits Name
      type alias GivenName as FirstName

      type alias NameList as FirstName[]

      model Person {
         name : FirstName
      }
      type alias Human as Person

   """.compiled()

      it("should calculate isAssignable correctly") {
         schema.type("FirstName").isAssignableTo(schema.type("Name")).should.be.`true`
         schema.type("GivenName").isAssignableTo(schema.type("Name")).should.be.`true`
         schema.type("Name").isAssignableTo(schema.type("FirstName")).should.be.`false`
         schema.type("Name").isAssignableTo(schema.type("GivenName")).should.be.`false`
      }

      it("should consider variance rules when calculating isAssignable") {
         schema.type("FirstName[]").isAssignableTo(schema.type("Name[]")).should.be.`true`
         schema.type("GivenName[]").isAssignableTo(schema.type("Name[]")).should.be.`true`
         schema.type("Name[]").isAssignableTo(schema.type("FirstName[]")).should.be.`false`
         schema.type("Name[]").isAssignableTo(schema.type("GivenName[]")).should.be.`false`
      }

      it("should consider variance rules across type aliases when calculating isAssignable") {
         schema.type("NameList").isAssignableTo(schema.type("FirstName[]")).should.be.`true`
         schema.type("FirstName[]").isAssignableTo(schema.type("NameList")).should.be.`true`
         schema.type("GivenName[]").isAssignableTo(schema.type("NameList")).should.be.`true`
         schema.type("NameList").isAssignableTo(schema.type("GivenName[]")).should.be.`true`
      }

      it("if types are declared as primitives they are assignable") {
         PrimitiveType.STRING.isAssignableTo(schema.type("FirstName")).should.be.`true`
         PrimitiveType.INTEGER.isAssignableTo(schema.type("FirstName")).should.be.`false`
      }

      it("is valid to assign a string to an enum") {
         val schema = """
            enum Country {
               NZ, UK
            }
            type CountryCode inherits String
         """.compiled()
         // Raw strings are ok...
         PrimitiveType.STRING.isAssignableTo(schema.type("Country")).shouldBeTrue()
         // ... but semantic strings are not:
         schema.type("CountryCode").isAssignableTo(schema.type("Country")).shouldBeFalse()
         // ... and other primitives are not
         PrimitiveType.INTEGER.isAssignableTo(schema.type("Country")).shouldBeFalse()
      }

      it("is not assignable when types have same base") {
         val schema = """
            type Age inherits Int
            type Id inherits Int
         """.compiled()
         schema.type("Age").isAssignableTo(schema.type("Id")).shouldBeFalse()
         schema.type("Id").isAssignableTo(schema.type("Age")).shouldBeFalse()
      }


      it("should resolve formatted types to their unformatted underlying type") {
         val schema = """
         type EventDate inherits Instant
         model Source {

             @Format("MM/dd/yy'T'HH:mm:ss.SSSX")
            eventDate : EventDate
         }
         model ThingWithInlineInstant {
            @Format("yyyy-MM-dd'T'HH:mm:ss.SSSX")
            eventDate : Instant
         }
      """.compiled()
         schema.type("EventDate").resolveAliases().qualifiedName.should.equal("EventDate")
         schema.objectType("Source").field("eventDate").type.resolveAliases().qualifiedName.should.equal("EventDate")
         schema.objectType("ThingWithInlineInstant").field("eventDate").type.resolveAliases().qualifiedName.should.equal("lang.taxi.Instant")
      }
   }
})
