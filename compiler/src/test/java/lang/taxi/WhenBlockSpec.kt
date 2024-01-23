package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.accessors.ConditionalAccessor
import lang.taxi.accessors.NullValue
import lang.taxi.expressions.LiteralExpression
import lang.taxi.types.EnumValue
import lang.taxi.types.WhenExpression

class WhenBlockSpec : DescribeSpec({
   describe("when block type checking") {
      it("is valid to use a column selector in a when block if the types match") {
         val errors = """
         type Notional inherits Decimal
         type AssetClass inherits String
         model Foo {
            assetClass : AssetClass by column("assetClass")
            value : Notional by when (this.assetClass) {
               "FXD" -> column("VALUE1")
               else -> column("VALUE2")
            }
         }
         """.validated()
         errors.should.be.empty
      }

      it("should detect type mismatch of value in when case selector") {
         val errors = """type Identifier inherits String
         type AssetClass inherits String
         model Foo {
            assetClass : AssetClass by column("assetClass")
            identifierValue : Identifier by when (this.assetClass) {
               11 -> left(column("SYMBOL"),6) // <-- This is an error, as 11 isn't compatible with asset class, which is a string
               else -> column("ISIN")
            }
         }""".validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Type mismatch. Type of lang.taxi.Int is not assignable to type AssetClass")
      }

      it("should detect a type mismatch of fields") {
         val errors = """
         type Identifier inherits Int
         type AssetClass inherits String
         type Name inherits String
         model Foo {
            assetClass : AssetClass by column("assetClass")
            identifierValue : Identifier by when (this.assetClass) {
               "foo" -> this.assetClass // <-- This is an error, as this.assetClass is a String, which isn't assignable to Identifier, which is a number
               else -> column("ISIN")
            }
         }""".validated()
            .shouldContainMessage("Type mismatch. Type of AssetClass is not assignable to type Identifier")
      }

      it("should not allow assignment of fields where the types are different but share a common primitive base type") {
         val errors = """
         type Identifier inherits String
         type AssetClass inherits String
         type Name inherits String
         model Foo {
            name : Name
            assetClass : AssetClass by column("assetClass")
            identifierValue : Identifier by when (this.assetClass) {
               "foo" -> this.name // <- name is a string, and Identifier are a string, but they aren't compatible.
               else -> column("ISIN")
            }
         }""".validated()
            .shouldContainMessage("Type mismatch. Type of Name is not assignable to type Identifier")
      }

      it("should detect type mismatch of value in when case selector") {
         val errors = """type Identifier inherits String
         type AssetClass inherits String
         model Foo {
            assetClass : AssetClass by column("assetClass")
            identifierValue : Identifier by when (this.assetClass) {
               "FXD" -> 12 // <-- This is an error, as 12 isn't compatible with Identifier, which is a string
               else -> column("ISIN")
            }
         }""".validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Type mismatch. Type of lang.taxi.Int is not assignable to type Identifier")
      }
      it("should allow assignment of null in when clause") {
         val whenBlock = """
            enum Country {
               NZ("New Zealand"),
               AUS("Australia")
            }
            model Person {
               identifiesAs : String
               country : Country by when (this.identifiesAs) {
                  "Kiwi" -> null
                  else -> Country.AUS
               }
            }
         """.compiled()
            .model("Person")
            .field("country").accessor!!.asA<WhenExpression>()
         whenBlock.cases[0].getSingleAssignment().assignment.asA<LiteralExpression>()
            .value.should.equal(NullValue)
      }

      it("should allow enums to be assigned in when clause") {
         val whenBlock = """
            enum Country {
               NZ("New Zealand"),
               AUS("Australia")
            }
            model Person {
               identifiesAs : String
               country : Country by when (this.identifiesAs) {
                  "Kiwi" -> Country.NZ
                  "Ozzie" -> Country.AUS
               }
            }
         """.compiled()
            .model("Person")
            .field("country").accessor!!.asA<WhenExpression>()
         val enumLiteral = whenBlock.cases[0].getSingleAssignment().assignment.asA<LiteralExpression>().literal
         val enumValue = enumLiteral.value.asA<EnumValue>()
         enumValue.qualifiedName.should.equal("Country.NZ")
      }

      // Disabling this for now.
      // Although this seems a good idea, it causes problems with ingesting strings to enums
      // and isn't particularly ergonimc in practice. Leaving the test here, as we may revisit.
      xit("should not allow strings in when clause against enum") {
         val errors = """
            enum Country {
               NZ("New Zealand"),
               AUS("Australia")
            }
            model Person {
               identifiesAs : String
               country : Country by when (this.identifiesAs) {
                  "Kiwi" -> "NZ"
                  "Ozzie" -> "AUS"
               }
            }
         """.validated().errors()
         errors.should.have.size(2)
         errors.first().detailMessage.should.equal("Type mismatch. Type of lang.taxi.String is not assignable to type Country")

      }
   }
})
