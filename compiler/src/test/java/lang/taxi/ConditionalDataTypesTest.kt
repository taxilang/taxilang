package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.*
import org.junit.Test

class ConditionalDataTypesTest {

   @Test
   fun simpleConditionalTypeGrammar() {
      val src = """
type TradeRecord {
    // define simple properties by xpath expressions
    ccy1 : Currency as String
    (   dealtAmount : String
        settlementAmount : String
    ) by when( xpath("/foo/bar") : String) {
        ccy1 -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = "GBP"
            )
        }
        "foo" -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = "EUR"
            )
        }
    }
}
      """.trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.type("TradeRecord") as ObjectType

      val dealtAmountCondition = type.field("dealtAmount").readExpression!!
      require(dealtAmountCondition is WhenFieldSetCondition)
      val selector = dealtAmountCondition.selectorExpression as AccessorExpressionSelector
      val accessor = selector.accessor as XpathAccessor
      accessor.expression.should.equal("/foo/bar")
      selector.declaredType.should.equal(PrimitiveType.STRING)

      dealtAmountCondition.cases.should.have.size(2)
      val case1 = dealtAmountCondition.cases[0]
      case1.matchExpression.should.satisfy {
         require(it is ReferenceCaseMatchExpression)
         it.reference.should.equal("ccy1")
         true
      }
      val dealtAmountCaseFieldAssignmentExpression = case1.assignments[0] as FieldAssignmentExpression
      dealtAmountCaseFieldAssignmentExpression.fieldName.should.equal("dealtAmount")
      val dealtAmountDestructuredAssignment = dealtAmountCaseFieldAssignmentExpression.assignment as DestructuredAssignment
      dealtAmountDestructuredAssignment.assignments.should.have.size(2)
      dealtAmountDestructuredAssignment.assignments[0].should.satisfy {
         it.fieldName.should.equal("quantity")
         val assignment = it.assignment as ScalarAccessorValueAssignment
         assignment.accessor.should.equal(XpathAccessor("/foo/bar/quantity1"))
         true
      }
      dealtAmountDestructuredAssignment.assignments[1].should.satisfy {
         it.fieldName.should.equal("currency")
         val assignment = it.assignment as ReferenceAssignment
         assignment.reference.should.equal("ccy1")
         true
      }
   }


   @Test
   fun canDeclareConditionalTypes() {
      val src = """
type Money {
    quantity : MoneyAmount as Decimal
    currency : CurrencySymbol as String
}

type DealtAmount inherits Money
type SettlementAmount inherits Money
type alias Ccy1 as String
type alias Ccy2 as String

enum QuoteBasis {
    Currency1PerCurrency2,
    Currency2PerCurrency1
}
type TradeRecord {
    // define simple properties by xpath expressions
    ccy1 : Ccy1 by xpath('/foo/bar/ccy1')
    ccy2 : Ccy2 by xpath('/foo/bar/ccy2')
    (   dealtAmount : DealtAmount
        settlementAmount : SettlementAmount
        quoteBasis: QuoteBasis
    ) by when( xpath("/foo/bar") : CurrencySymbol) {
        ccy1 -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = ccy2
            )
            quoteBasis = QuoteBasis.Currency1PerCurrency2
        }
        ccy2 -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity2")
                currency = ccy1
            )
            settlementAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency = ccy2
            )
            quoteBasis  = QuoteBasis.Currency2PerCurrency1
        }
    }
}
      """.trimIndent()
      val doc = Compiler(src).compile()
   }


   @Test
   fun `can use legacy when syntax - without the this prefix`() {
      val src = """
      type Direction inherits String
      type BankDirection inherits Direction
      type ClientDirection inherits Direction
      type Order {
         bankDirection: BankDirection
         // Note: This is the point of the test
         // that there's no this. before the bankDirection field
         clientDirection: ClientDirection by when (bankDirection) {
            "Buy" -> "Sell"
            "Sell" -> "Buy"
         }
      }
      """
      val doc = Compiler(src).compile()
      val order = doc.objectType("Order")
      order.fields.should.have.size(2)
      val clientDirection = order.field("clientDirection")
      val accessor = clientDirection.accessor as ConditionalAccessor
      val condition = accessor.expression as WhenFieldSetCondition
      condition.cases.should.have.size(2)
      // Buy -> Sell
      (condition.cases[0].matchExpression as LiteralCaseMatchExpression).value.should.equal("Buy")
      ((condition.cases[0].assignments[0] as InlineAssignmentExpression).assignment as LiteralAssignment).value.should.equal("Sell")

      // Sell -> Buy
      (condition.cases[1].matchExpression as LiteralCaseMatchExpression).value.should.equal("Sell")
      ((condition.cases[1].assignments[0] as InlineAssignmentExpression).assignment as LiteralAssignment).value.should.equal("Buy")
   }

   @Test
   fun canDeclareSingleFieldConditionalAssignments() {
      val src = """
      type Direction inherits String
      type BankDirection inherits Direction
      type ClientDirection inherits Direction
      type Order {
         bankDirection: BankDirection
         clientDirection: ClientDirection by when (this.bankDirection) {
            "Buy" -> "Sell"
            "Sell" -> "Buy"
         }
      }
      """
      val doc = Compiler(src).compile()
      val order = doc.objectType("Order")
      order.fields.should.have.size(2)
      val clientDirection = order.field("clientDirection")
      val accessor = clientDirection.accessor as ConditionalAccessor
      val condition = accessor.expression as WhenFieldSetCondition
      condition.cases.should.have.size(2)
      // Buy -> Sell
      (condition.cases[0].matchExpression as LiteralCaseMatchExpression).value.should.equal("Buy")
      ((condition.cases[0].assignments[0] as InlineAssignmentExpression).assignment as LiteralAssignment).value.should.equal("Sell")

      // Sell -> Buy
      (condition.cases[1].matchExpression as LiteralCaseMatchExpression).value.should.equal("Sell")
      ((condition.cases[1].assignments[0] as InlineAssignmentExpression).assignment as LiteralAssignment).value.should.equal("Buy")
   }

   @Test
   fun canDeclareSingleFieldConditionalAssignmentsUsingEnumsInAssignment() {
      val src = """
      type Direction inherits String
      enum PayReceive {
         Pay,
         Receive
      }
      type BankDirection inherits Direction
      type ClientDirection inherits Direction
      type Order {
         bankDirection: BankDirection
         payReceive: PayReceive by when (this.bankDirection) {
            "Buy" -> PayReceive.Pay
            "Sell" -> PayReceive.Receive
            else -> null
         }
      }
      """
      val doc = Compiler(src).compile()
      val order = doc.objectType("Order")
      order.fields.should.have.size(2)
      val payReceive = order.field("payReceive")
      val accessor = payReceive.accessor as ConditionalAccessor
      val condition = accessor.expression as WhenFieldSetCondition
      condition.cases.should.have.size(3)
      // Buy -> Sell
      (condition.cases[0].matchExpression as LiteralCaseMatchExpression).value.should.equal("Buy")
      val assignment = ((condition.cases[0].assignments[0] as InlineAssignmentExpression).assignment as EnumValueAssignment)
      assignment.enum.qualifiedName.should.equal("PayReceive")
      assignment.enumValue.name.should.equal("Pay")

      condition.cases[2].matchExpression.should.be.instanceof(ElseMatchExpression::class.java)
      (condition.cases[2].assignments[0] as InlineAssignmentExpression).assignment.should.be.instanceof(NullAssignment::class.java)
   }

   @Test
   fun canUseEnumsOnLhsOfWhen() {
      val src = """
         enum FixedOrFloatLeg {
             Fixed,
             Float
         }
         enum Leg1FixedOrFloat inherits FixedOrFloatLeg
         enum Leg2FixedOrFloat inherits FixedOrFloatLeg
         type Leg1Index inherits String
         type Leg2Index inherits String

         type Trade {
            s0_TypeStr : Leg1FixedOrFloat by jsonPath("/S0_TypeStr")
            s0_Index : Leg1Index by jsonPath("/S0_Index")
            s1_Index : Leg2Index by jsonPath("/S1_Index")
            underlyingIndex : String by when (this.s0_TypeStr) {
               FixedOrFloatLeg.Float -> s0_Index
               else -> s1_Index
            }
         }
      """.trimIndent()
      val doc = Compiler(src).compile()
      val accessor = doc.objectType("Trade").field("underlyingIndex").accessor as ConditionalAccessor
      val fieldSet = accessor.expression as WhenFieldSetCondition
      val enumExpression = fieldSet.cases[0].matchExpression as EnumLiteralCaseMatchExpression
      enumExpression.enumValue.qualifiedName.should.equal("FixedOrFloatLeg.Float")
   }

   @Test
   fun `using an enum with an invalid value in a when block should error`() {
      val src = """
          enum FixedOrFloatLeg {
             Fixed,
             Float
         }
         type Trade {
            s0_TypeStr : FixedOrFloatLeg
            s0_Index : Leg1Index as String
            s1_Index : Leg2Index as String
            underlyingIndex : String by when (this.s0_TypeStr) {
               FixedOrFloatLeg.Fooball -> s0_Index
               else -> s1_Index
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "'Fooball' is not defined on enum FixedOrFloatLeg"
      } }
   }

   @Test
   fun `using an enum in a when block against a type that isn't defined should error`() {
      val src = """
         type Trade {
            s0_TypeStr : Leg1FixedOrFloat as String
            s0_Index : Leg1Index as String
            s1_Index : Leg2Index as String
            underlyingIndex : String by when (this.s0_TypeStr) {
               FixedOrFloatLeg.Float -> s0_Index
               else -> s1_Index
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "FixedOrFloatLeg is not defined"
      } }
   }
}
