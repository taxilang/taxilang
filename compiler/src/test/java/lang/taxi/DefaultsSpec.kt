package lang.taxi

import com.winterbe.expekt.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.math.BigDecimal

object DefaultsSpec : Spek({
   describe("defaults on objects") {
      it("is possible to define default values") {
         val schema = """
         enum Foo {
            One,
            Two
         }
         type FirstName inherits String
         type Age inherits Int
         type Height inherits Decimal
         model Person {
            name : FirstName by default('jimmy')
            age: Age by default(42)
            foo: Foo by default(Foo.One)
            decimal : Decimal by default(12.34)
            decimal2 : Decimal by default(1000000.0)
            negativeInt : Int by default(-10)
            negativeDecimal : Decimal by default(-12.34)
         }
        """.compiled()

         val person = schema.objectType("Person")
         person.field("name").accessorDefault.should.equal("jimmy")
         person.field("age").accessorDefault.should.equal(42)
         person.field("negativeInt").accessorDefault.should.equal(-10)
         person.field("decimal").accessorDefault.should.equal(BigDecimal("12.34"))
         person.field("negativeDecimal").accessorDefault.should.equal(BigDecimal("-12.34"))
         person.field("foo").accessorDefault.should.equal(
            schema.enumType("Foo").of("One")
         )
      }
      it("generates compiler errors when default assignments are the wrong type") {
         val errors = """
         enum Foo {
            One,
            Two
         }
         type FirstName inherits String
         type Age inherits Int
         model Person {
            name : FirstName by default(1)
            age: Age by default("")
            foo : Foo by default(Foo.Three)
         }
        """.validated()
         errors[0].detailMessage.should.equal("Default value 1 is not compatible with lang.taxi.String")
         errors[1].detailMessage.should.equal("Default value  is not compatible with lang.taxi.Int")
         errors[2].detailMessage.should.equal("Foo has no value of Foo.Three")
      }

   }
})
