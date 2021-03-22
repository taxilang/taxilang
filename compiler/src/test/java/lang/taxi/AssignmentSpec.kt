package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.compiler.isAssignableTo
import lang.taxi.compiler.resolveAliases
import lang.taxi.types.PrimitiveType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AssignmentSpec : Spek({
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


      it("should resolve formatted types to their unformatted underlying type") {
         val schema = """
         type EventDate inherits Instant
         model Source {
            eventDate : EventDate( @format = "MM/dd/yy'T'HH:mm:ss.SSSX" )
         }
         model ThingWithInlineInstant {
            eventDate : Instant( @format = "yyyy-MM-dd'T'HH:mm:ss.SSSX" )
         }
      """.compiled()
         schema.type("EventDate").resolveAliases().qualifiedName.should.equal("EventDate")
         schema.objectType("Source").field("eventDate").type.resolveAliases().qualifiedName.should.equal("EventDate")
         schema.objectType("ThingWithInlineInstant").field("eventDate").type.resolveAliases().qualifiedName.should.equal("lang.taxi.Instant")
      }
   }
})
