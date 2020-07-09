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

      val dealtAmountCondition = type.field("dealtAmount").readCondition!!
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
   fun canDeclareSingleFieldConditionalAssignments() {
      val src = """
      type Direction inherits String
      type BankDirection inherits Direction
      type ClientDirection inherits Direction
      type Order {
         bankDirection: BankDirection
         clientDirection: ClientDirection by when (BankDirection) {
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
      val condition = accessor.condition as WhenFieldSetCondition
      condition.cases.should.have.size(2)
      // Buy -> Sell
      (condition.cases[0].matchExpression as LiteralCaseMatchExpression).value.should.equal("Buy")
      ((condition.cases[0].assignments[0] as InlineAssignmentExpression).assignment as LiteralAssignment).value.should.equal("Sell")

      // Sell -> Buy
      (condition.cases[1].matchExpression as LiteralCaseMatchExpression).value.should.equal("Sell")
      ((condition.cases[1].assignments[0] as InlineAssignmentExpression).assignment as LiteralAssignment).value.should.equal("Buy")
   }
}
