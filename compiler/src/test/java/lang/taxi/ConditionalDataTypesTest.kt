package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.accessors.ConditionalAccessor
import lang.taxi.accessors.XpathAccessor
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.AccessorExpressionSelector
import lang.taxi.types.AndExpression
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperator
import lang.taxi.types.ConstantEntity
import lang.taxi.types.DestructuredAssignment
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EnumLiteralCaseMatchExpression
import lang.taxi.types.EnumValueAssignment
import lang.taxi.types.FieldAssignmentExpression
import lang.taxi.types.FieldReferenceEntity
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.LiteralAssignment
import lang.taxi.types.LiteralCaseMatchExpression
import lang.taxi.types.NullAssignment
import lang.taxi.types.ObjectType
import lang.taxi.types.OrExpression
import lang.taxi.types.PrimitiveType
import lang.taxi.types.ReferenceAssignment
import lang.taxi.types.ReferenceCaseMatchExpression
import lang.taxi.types.ScalarAccessorValueAssignment
import lang.taxi.types.WhenFieldSetCondition
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ConditionalDataTypesTest {

   @Test
   @Disabled("Destructured types are currently disabled")
   fun simpleConditionalTypeGrammar() {
      val src = """
         type Money {
             quantity : MoneyAmount as Decimal
             currency : CurrencySymbol as String
         }

         type DealtAmount inherits Money
         type SettlementAmount inherits Money
type TradeRecord {
    // define simple properties by xpath expressions
    ccy1 : Currency as String
    (   dealtAmount : DealtAmount
        settlementAmount : SettlementAmount
    ) by when( xpath("/foo/bar") : String) {
        ccy1 -> {
            dealtAmount(
                quantity by xpath("/foo/bar/quantity1")
                currency == ccy1
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
         (assignment.accessor as XpathAccessor).path.should.equal("/foo/bar/quantity1")
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
   @Disabled("Destructured types are currently disabled")
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
   @Disabled("Deprecating this syntax")
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
               Leg1FixedOrFloat.Float -> s0_Index
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
   fun `can use functions on rhs of a when block`() {
      val field = Compiler("""
         type Identifier inherits String
         type AssetClass inherits String
         model Foo {
            assetClass : AssetClass by column("assetClass")
            identifierValue : Identifier by when (this.assetClass) {
               "FXD" -> left(column("SYMBOL"),6)
               else -> column("ISIN")
            }
         }
      """).compile().objectType("Foo").field("identifierValue")
      val whenCondition = (field.accessor as ConditionalAccessor).expression as WhenFieldSetCondition
      val assignmentExpression = whenCondition.cases[0].assignments[0] as InlineAssignmentExpression
      val assingment = assignmentExpression.assignment as ScalarAccessorValueAssignment
      val accessor = assingment.accessor as FunctionAccessor
      accessor.function.qualifiedName.should.equal("taxi.stdlib.left")
      // function parsing is tested elsewhere
   }

   @Test
   fun `can use functions inside when clauses`() {
     val field = Compiler( """
      type Order {
         bankDirection: BankDirection as String
         clientDirection: ClientDirection as String by when (upperCase(this.bankDirection) : String) {
            "BUY" -> "Sell"
            "SELL" -> "Buy"
            else -> null
         }
      }
      """).compile().objectType("Order").field("clientDirection")
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

   @Test
   fun `Complex when condition expressions are allowed`() {
      val src = """
         model ComplexWhen {
            trader: String
            status: String
            initialQuantity: Decimal
            leavesQuantity: Decimal
            quantityStatus: String by when {
                this.initialQuantity == this.leavesQuantity -> "ZeroFill"
                this.trader == "Marty" || this.status == "Pending" -> "ZeroFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.trader == "Marty" || this.status == "Pending"  -> "FullyFilled"
                this.leavesQuantity == null && this.initialQuantity != null -> trader
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val field = Compiler(src).compile().objectType("ComplexWhen").field("quantityStatus")
      val whenCondition = (field.accessor as ConditionalAccessor).expression as WhenFieldSetCondition
      whenCondition.cases.size.should.equal(6)
      val case1 = whenCondition.cases[0].matchExpression as ComparisonExpression
      case1.left.should.equal(FieldReferenceEntity("initialQuantity"))
      case1.right.should.equal(FieldReferenceEntity("leavesQuantity"))
      case1.operator.should.equal(ComparisonOperator.EQ)

      val case2 = whenCondition.cases[1].matchExpression as OrExpression
      case2.left.should.equal(ComparisonExpression(ComparisonOperator.EQ, FieldReferenceEntity("trader"), ConstantEntity("Marty")))
      case2.right.should.equal(ComparisonExpression(ComparisonOperator.EQ, FieldReferenceEntity("status"), ConstantEntity("Pending")))

      val case3 = whenCondition.cases[2].matchExpression as AndExpression
      case3.left.should.equal(ComparisonExpression(ComparisonOperator.GT, FieldReferenceEntity("leavesQuantity"), ConstantEntity(0)))
      case3.right.should.equal(ComparisonExpression(ComparisonOperator.LT, FieldReferenceEntity("leavesQuantity"), FieldReferenceEntity("initialQuantity")))

      val case4 = whenCondition.cases[3].matchExpression as OrExpression
      case4.left.should.equal(OrExpression(
         AndExpression(
            ComparisonExpression(ComparisonOperator.GT, FieldReferenceEntity("leavesQuantity"), ConstantEntity(0)),
            ComparisonExpression(ComparisonOperator.LT, FieldReferenceEntity("leavesQuantity"), FieldReferenceEntity("initialQuantity"))
         ),
         ComparisonExpression(ComparisonOperator.EQ, FieldReferenceEntity("trader"), ConstantEntity("Marty"))
      ))
      case4.right.should.equal(
         ComparisonExpression(ComparisonOperator.EQ, FieldReferenceEntity("status"), ConstantEntity("Pending"))
      )

      val case5 = whenCondition.cases[4].matchExpression as AndExpression
      case5.left.should.equal(ComparisonExpression(ComparisonOperator.EQ, FieldReferenceEntity("leavesQuantity"), ConstantEntity(null)))
      case5.right.should.equal(ComparisonExpression(ComparisonOperator.NQ, FieldReferenceEntity("initialQuantity"), ConstantEntity(null)))

   }

   @Test
   fun `When Complex when condition expressions references an invalid field compilation error is provided`() {
      val src = """
         model ComplexWhen {
            trader: String
            status: String
            initialQuantity: Decimal
            leavesQuantity: Decimal
            quantityStatus: String by when {
                this.initialQuantity == this.leavesQuantity -> "ZeroFill"
                this.trader == "Marty" || this.status == "Pending" -> "ZeroFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity -> "PartialFill"
                this.leavesQuantity > 0 && this.leavesQuantity < this.initialQuantity || this.user == "Marty" || this.status == "Pending"  -> "FullyFilled"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "user is not a field of ComplexWhen"
      } }
   }

   @Test
   fun `When Complex when condition expressions has a numeric comparison for string field compilation error thrown`() {
      val src = """
         model ComplexWhen {
            trader: String
            quantityStatus: String by when {
                this.trader == 0 -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "trader is not a numeric based field of ComplexWhen"
      } }
   }

   @Test
   fun `When Complex when condition expressions has a string comparison for a numeric field compilation error thrown`() {
      val src = """
         model ComplexWhen {
            qty: Decimal
            quantityStatus: String by when {
                this.qty == '0' -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "qty is not a String based field of ComplexWhen"
      } }
   }

   @Test
   fun `When Complex when condition expressions has a greater than comparison for a string field compilation error thrown`() {
      val src = """
         model ComplexWhen {
            status: String
            quantityStatus: String by when {
                this.status > '0' -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "> is not applicable to status field of ComplexWhen"
      } }
   }

   @Test
   fun `When Complex when condition expressions has a less than comparison for a string field compilation error thrown`() {
      val src = """
         model ComplexWhen {
            status: String
            quantityStatus: String by when {
                this.status < '0' -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "< is not applicable to status field of ComplexWhen"
      } }
   }

   @Test
   fun `When Complex when condition expressions has a greater than equals comparison for a string field compilation error thrown`() {
      val src = """
         model ComplexWhen {
            status: String
            quantityStatus: String by when {
                this.status >= '0' -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == ">= is not applicable to status field of ComplexWhen"
      } }
   }

   @Test
   fun `When Complex when condition expressions has a less than equals comparison for a string field compilation error thrown`() {
      val src = """
         model ComplexWhen {
            status: String
            quantityStatus: String by when {
                this.status <= '0' -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "<= is not applicable to status field of ComplexWhen"
      } }
   }

   @Test
   fun `When Complex when condition expressions has a selector  compilation error thrown`() {
      val src = """
         model ComplexWhen {
            status: String
            quantityStatus: String by when(this.status) {
                this.status != 'trading' -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "when case for quantityStatus in ComplexWhen cannot have reference selector use when { .. } syntax"
      } }
   }

   @Test
   fun `for when statements with no reference selectors only logical when cases are allowed`() {
      val src = """
         model ComplexWhen {
            status: String
            quantityStatus: String by when {
                "trading" -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "when case for quantityStatus in ComplexWhen can only logical expression when cases"
      } }
   }

   @Test
   fun `model attribute based definitions are no allowed`() {
      val src = """
         type Status inherits String
         type QtyStatus inherits String
         model ComplexWhen {
            status: Status
            quantityStatus: QtyStatus by when {
                ComplexWhen::Status != null -> "ZeroFill"
                else -> "CompleteFill"
            }
         }
      """.trimIndent()
      val errors = Compiler(src).validate()
      errors.should.satisfy { it.any { error ->
         error.detailMessage == "SourceType::FieldType notation is only allowed in view definitions"
      } }
   }
}
