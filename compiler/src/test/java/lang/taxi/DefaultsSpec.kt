package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import lang.taxi.expressions.LiteralExpression
import lang.taxi.types.Field
import java.math.BigDecimal

class DefaultsSpec : DescribeSpec({
   describe("defaults on objects") {
      it("is possible to define default values") {
         fun Field.shouldHaveDefaultOf(value: Any) {
            this.accessor!!.asA<LiteralExpression>().literal.value.shouldBe(value)
         }

         val schema = """
         enum Foo {
            One,
            Two
         }
         type FirstName inherits String
         type Age inherits Int
         type Height inherits Decimal
         model Person {
            name : FirstName = "jimmy"
            age: Age = 42
            foo: Foo = Foo.One
            decimal : Decimal = 12.34
            decimal2 : Decimal = 1000000.0
            negativeInt : Int = -10
            negativeDecimal : Decimal  = -12.34
         }
        """.compiled()

         val person = schema.objectType("Person")
         person.field("name").shouldHaveDefaultOf("jimmy")
         person.field("age").shouldHaveDefaultOf(42)
         person.field("negativeInt").shouldHaveDefaultOf(-10)
         person.field("decimal").shouldHaveDefaultOf(BigDecimal("12.34"))
         person.field("negativeDecimal").shouldHaveDefaultOf(BigDecimal("-12.34"))
         person.field("foo").shouldHaveDefaultOf(
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
            name : FirstName  = 1
            age: Age  = ""
            foo : Foo  = Foo.Three
         }
        """.validated()
         errors[0].detailMessage.should.equal("Type mismatch. Type of lang.taxi.Int is not assignable to type FirstName")
         errors[1].detailMessage.should.equal("Type mismatch. Type of lang.taxi.String is not assignable to type Age")
         errors[2].detailMessage.should.equal("Foo does not have a member Three")
      }

   }
})
