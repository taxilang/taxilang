package lang.taxi

import com.winterbe.expekt.should
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object WhenBlockSpec : Spek({
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
         errors.first().detailMessage.should.equal("Type mismatch.  Found a type of lang.taxi.Int where a AssetClass is expected")
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
         errors.first().detailMessage.should.equal("Type mismatch.  Found a type of lang.taxi.Int where a Identifier is expected")
      }
   }
})
