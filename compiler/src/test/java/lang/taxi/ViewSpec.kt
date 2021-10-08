package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.accessors.ConditionalAccessor
import lang.taxi.accessors.LiteralAccessor
import lang.taxi.expressions.LiteralExpression
import lang.taxi.expressions.OperatorExpression
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.FormulaOperator
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.ObjectType
import lang.taxi.types.WhenFieldSetCondition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ViewSpec : Spek({
   describe("view syntax") {
      it("simple view definition") {
         val src = """
         model Person {
            firstName : FirstName inherits String
            lastName : LastName inherits String
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] } as {
               personName: Person::FirstName
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         taxiDocument.model("Person").should.not.be.`null`
         val personView = taxiDocument.view("PersonView")
         personView.should.not.be.`null`
         personView?.typeDoc.should.equal("Sample View")
      }

      it("view with a namespace") {
         val src = """
            namespace notional
            {
               type LastName inherits String
               model Person {
                  firstName : FirstName inherits String
                  lastName : LastName
               }
           }
           """.trimIndent()

         val viewSrc = """
            import notional.Person;
            import notional.LastName;
            namespace notional.views {
                [[
                 Sample View
                ]]
               view PersonView with query {
                  find { Person[] } as  {
                      surname: Person::LastName
                  }
               }
            }
         """.trimIndent()
         val taxiDocument = Compiler.forStrings(listOf(src, viewSrc)).compile()
         taxiDocument.model("notional.Person").should.not.be.`null`
         val personView = taxiDocument.view("notional.views.PersonView")
         personView.should.not.be.`null`
         personView?.typeDoc.should.equal("Sample View")
      }

      it("A view with multiple view statements") {
         val src = """
         type OrderId inherits String
         model OrderSent {
            sentOrderId : OrderId
         }

         model OrderFill {
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] } as { id: OrderSent::OrderId },
            find { OrderSent[] (joinTo OrderFill[]) } as { id: OrderSent::OrderId }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         taxiDocument.model("OrderFill").should.not.be.`null`
         taxiDocument.model("OrderSent").should.not.be.`null`
         val orderView = taxiDocument.view("OrderView")
         orderView.should.not.be.`null`
         orderView?.typeDoc.should.equal("Sample View")
         orderView?.viewBodyDefinitions?.size.should.equal(2)
         orderView?.viewBodyDefinitions?.first()?.bodyType?.qualifiedName?.should?.equal("OrderSent")
         orderView?.viewBodyDefinitions?.get(1)?.bodyType?.qualifiedName?.should?.equal("OrderSent")
         orderView?.viewBodyDefinitions?.get(1)?.joinType?.qualifiedName?.should?.equal("OrderFill")
      }

      it("A join view with two types with multiple join fields can not be compiled") {
         val src = """
         type OrderId inherits String
         model OrderSent {
            sentOrderId : OrderId
            tempOrderId : OrderId
         }

         model OrderFill {
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] (joinTo OrderFill[]) }
         }
           """.trimIndent()
         val (compilationErrors, _) = Compiler(src).compileWithMessages()
         compilationErrors.last().detailMessage.should.equal("OrderSent and OrderFill can't be joined. Ensure that both types in join expression has a single property with Id annotation.")
      }

      it("invalid view definition due to conflicting as block in two find statements") {
         val src = """
         type OrderId inherits String
         type OrderQty inherits Decimal
         model OrderSent {
            sentOrderId : OrderId
         }

         model OrderFill {
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] } as { qty: OrderQty },
            find { OrderSent[] (joinTo OrderFill[]) } as { id: OrderSent.OrderId}
         }
           """.trimIndent()
         val (compilationErrors, _) = Compiler(src).compileWithMessages()
         compilationErrors.last().detailMessage.should.equal("Invalid View Definition - individual find expressions should have compatible 'as' blocks.")
      }

      it("invalid view definition due to field definition in as block") {
         val src = """
         type OrderId inherits String
         type OrderQty inherits Decimal

         model ModelFoo {
            sentOrderId : OrderId
         }
         model OrderSent {
            sentOrderId : OrderId
         }

         model OrderFill {
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] } as { id: ModelFoo::OrderId },
            find { OrderSent[] (joinTo OrderFill[]) } as { id: OrderSent::OrderId}
         }
           """.trimIndent()
         val (compilationErrors, _) = Compiler(src).compileWithMessages()
         compilationErrors.first().detailMessage.should.equal("Invalid View Definition - ModelFoo is not valid to use!")
      }

      it("A join view with with types with a single joinable field") {
         val src = """
         type OrderId inherits String
         type OrderQty inherits Decimal
         model OrderSent {
            sentOrderId : OrderId
         }

         model OrderFill {
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] (joinTo OrderFill[]) } as {
              id: OrderSent::OrderId
            }
         }
           """.trimIndent()
         val (compilationErrors, taxiDoc) = Compiler(src).compileWithMessages()
         compilationErrors.should.be.empty
         taxiDoc.views.first().viewBodyDefinitions!!.first().joinInfo!!.mainField.name.should.equal("sentOrderId")
         taxiDoc.views.first().viewBodyDefinitions!!.first().joinInfo!!.joinField.name.should.equal("fillOrderId")
      }


      it("A view with two find statements") {
         val src = """
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits Decimal
         type OrderStatus inherits String
         type DecimalFieldOrderFilled inherits Decimal


         model OrderSent {
            @Id
            sentOrderId : SentOrderId
            @Between
		      orderDateTime: OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss") by column("Time Submitted")
            orderType: OrderType by default("Market")
            subSecurityType: SecurityDescription? by column("Instrument Desc")
            requestedQuantity: RequestedQuantity? by column("Size")
            entryType: OrderStatus by default("New")
         }

         model OrderFill {
           @Id
           fillOrderId: FillOrderId
           orderType: OrderType by default("Market")
           subSecurityType: SecurityDescription? by column("Instrument Desc")
           executedQuantity: DecimalFieldOrderFilled? by column("Quantity")
           entryType: OrderStatus by default("Filled")
         }

         model OrderEvent { }

          [[
           Sample View
          ]]
         view OrderView inherits OrderEvent with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              orderDateTime: OrderSent::OrderEventDateTime
              orderType: OrderSent::OrderType
              subSecurityType: OrderSent::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderSent::OrderStatus
            },
            find { OrderSent[] (joinTo OrderFill[]) } as {
              orderId: OrderFill::FillOrderId
              orderDateTime: OrderEventDateTime
              orderType: OrderFill::OrderType
              subSecurityType: OrderFill::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderStatus by when {
                 OrderSent::RequestedQuantity == OrderFill::DecimalFieldOrderFilled -> OrderFill::OrderStatus
                 else -> "PartiallyFilled"
              }
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val orderFillType = taxiDocument.model("OrderFill")
         val orderSentType = taxiDocument.model("OrderSent")
         val orderStatusType = taxiDocument.type("OrderStatus")
         val requestedQuantityType = taxiDocument.type("RequestedQuantity")
         val decimalFieldOrderFilled = taxiDocument.type("DecimalFieldOrderFilled")
         orderFillType.should.not.be.`null`
         orderSentType.should.not.be.`null`
         val orderView = taxiDocument.view("OrderView")
         orderView.should.not.be.`null`
         orderView?.typeDoc.should.equal("Sample View")
         orderView?.viewBodyDefinitions?.size.should.equal(2)
         val findOrderSent = orderView!!.viewBodyDefinitions!!.first()
         val joinOrderSentOrderFill = orderView.viewBodyDefinitions!![1]
         findOrderSent.bodyType.qualifiedName.should.equal("OrderSent")
         val bodyType = (findOrderSent.viewBodyType!! as ObjectType)
         bodyType.anonymous.should.be.`true`
         bodyType.field("orderId").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("orderDateTime").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("orderType").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("subSecurityType").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("requestedQuantity").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("orderEntry").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         joinOrderSentOrderFill.bodyType.qualifiedName.should.equal("OrderSent")
         joinOrderSentOrderFill.joinType!!.qualifiedName.should.equal("OrderFill")
         val joinBodyType = (joinOrderSentOrderFill.viewBodyType!! as ObjectType)
         joinBodyType.anonymous.should.be.`true`
         joinBodyType.field("orderId").memberSource?.fullyQualifiedName.should.equal("OrderFill")
         joinBodyType.field("orderDateTime").memberSource?.fullyQualifiedName.should.be.`null`
         joinBodyType.field("orderType").memberSource?.fullyQualifiedName.should.equal("OrderFill")
         joinBodyType.field("subSecurityType").memberSource?.fullyQualifiedName.should.equal("OrderFill")
         joinBodyType.field("requestedQuantity").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         val whenField = joinBodyType.field("orderEntry")
         whenField.type.qualifiedName.should.equal("OrderStatus")
         whenField.accessor.should.instanceof(ConditionalAccessor::class.java)
         val conditionalAccessor = whenField.accessor as ConditionalAccessor
         conditionalAccessor.expression.should.instanceof(WhenFieldSetCondition::class.java)
         val whenFieldSetCondition = conditionalAccessor.expression as WhenFieldSetCondition
         whenFieldSetCondition.selectorExpression.should.instanceof(LiteralExpression::class.java)
         (whenFieldSetCondition.selectorExpression as LiteralExpression).literal.should.equal(
            LiteralAccessor(true)
         )
         val firstCase = whenFieldSetCondition.cases.first()
         ((firstCase.matchExpression as OperatorExpression)
            .lhs as ModelAttributeReferenceSelector).memberType.should.equal(requestedQuantityType)
         ((firstCase.matchExpression as OperatorExpression)
            .lhs as ModelAttributeReferenceSelector).memberSource.should.equal(orderSentType.toQualifiedName())
         ((firstCase.matchExpression as OperatorExpression)
            .rhs as ModelAttributeReferenceSelector).memberType.should.equal(decimalFieldOrderFilled)
         ((firstCase.matchExpression as OperatorExpression)
            .rhs as ModelAttributeReferenceSelector).memberSource.should.equal(orderFillType.toQualifiedName())
         (firstCase.matchExpression as OperatorExpression).operator.should.equal(FormulaOperator.Equal)
         (firstCase.assignments.first().assignment as ModelAttributeReferenceSelector)
            .memberType.should.equal(orderStatusType)
         (firstCase.assignments.first().assignment as ModelAttributeReferenceSelector)
            .memberSource.should.equal(orderFillType.toQualifiedName())
         whenFieldSetCondition.cases[1].matchExpression.should.equal(ElseMatchExpression)
         (whenFieldSetCondition.cases[1].assignments.first().assignment as LiteralExpression)
            .value
            .should.equal("PartiallyFilled")
      }


      it("A view with one find statement") {
         val src = """
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits Decimal
         type OrderStatus inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type OrderSize inherits String

         model OrderSent {
            @Id
            sentOrderId : SentOrderId
            @Between
		      orderDateTime: OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss") by column("Time Submitted")
            orderType: OrderType by default("Market")
            subSecurityType: SecurityDescription? by column("Instrument Desc")
            requestedQuantity: RequestedQuantity? by column("Size")
            entryType: OrderStatus by default("New")
         }

         model OrderEvent { }



          [[
           Sample View
          ]]
         view OrderView inherits OrderEvent with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              orderDateTime: OrderSent::OrderEventDateTime
              orderType: OrderSent::OrderType
              subSecurityType: OrderSent::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderSent::OrderStatus
              orderSize: OrderSize by when {
                 OrderSent::RequestedQuantity == 0 -> "Zero Size"
                 OrderSent::RequestedQuantity > 0 && OrderSent::RequestedQuantity < 100 -> "Small Size"
                 else -> "PartiallyFilled"
              }
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val orderSentType = taxiDocument.model("OrderSent")
         val requestedQtyType = taxiDocument.type(("RequestedQuantity"))
         orderSentType.should.not.be.`null`
         val orderView = taxiDocument.view("OrderView")
         orderView.should.not.be.`null`
         orderView?.typeDoc.should.equal("Sample View")
         orderView?.viewBodyDefinitions?.size.should.equal(1)
         val findOrderSent = orderView!!.viewBodyDefinitions!!.first()
         findOrderSent.bodyType.qualifiedName.should.equal("OrderSent")
         val bodyType = (findOrderSent.viewBodyType!! as ObjectType)
         bodyType.anonymous.should.be.`true`
         bodyType.field("orderId").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("orderDateTime").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("orderType").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("subSecurityType").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("requestedQuantity").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         bodyType.field("orderEntry").memberSource?.fullyQualifiedName.should.equal("OrderSent")
         val whenField = bodyType.field("orderSize")
         val conditionalAccessor = whenField.accessor as ConditionalAccessor
         conditionalAccessor.expression.should.instanceof(WhenFieldSetCondition::class.java)
         val whenFieldSetCondition = conditionalAccessor.expression as WhenFieldSetCondition
         whenField.type.qualifiedName.should.equal("OrderSize")
         whenFieldSetCondition.selectorExpression.should.instanceof(LiteralExpression::class.java)
         (whenFieldSetCondition.selectorExpression as LiteralExpression).literal.should.equal(
            LiteralAccessor(true)
         )
         val firstCase = whenFieldSetCondition.cases.first()
         ((firstCase.matchExpression as OperatorExpression)
            .lhs as ModelAttributeReferenceSelector).memberType.should.equal(requestedQtyType)
         ((firstCase.matchExpression as OperatorExpression)
            .lhs as ModelAttributeReferenceSelector).memberSource.should.equal(orderSentType.toQualifiedName())
         ((firstCase.matchExpression as OperatorExpression)
            .rhs as LiteralExpression).value.should.equal(0)
         ((firstCase.matchExpression as OperatorExpression)
            .rhs as LiteralExpression).literal.value.should.equal(0)
         (firstCase.matchExpression as OperatorExpression).operator.should.equal(FormulaOperator.Equal)
         ((firstCase.assignments.first().assignment as LiteralExpression)
            .value.should.equal("Zero Size"))

         val secondCase = whenFieldSetCondition.cases[1]
         (((secondCase.matchExpression as OperatorExpression)
            .lhs as OperatorExpression)
            .lhs as ModelAttributeReferenceSelector)
            .memberType.should.equal(requestedQtyType)
         (((secondCase.matchExpression as OperatorExpression)
            .lhs as OperatorExpression)
            .rhs as LiteralExpression)
            .value.should.equal(0)
         (((secondCase.matchExpression as OperatorExpression)
            .rhs as OperatorExpression)
            .lhs as ModelAttributeReferenceSelector)
            .memberType.should.equal(requestedQtyType)
         (((secondCase.matchExpression as OperatorExpression)
            .rhs as OperatorExpression)
            .rhs as LiteralExpression)
            .value.should.equal(100)
         (secondCase.matchExpression as OperatorExpression).operator.should.equal(FormulaOperator.LogicalAnd)
         ((secondCase.assignments.first().assignment as LiteralExpression)
            .value.should.equal("Small Size"))

         whenFieldSetCondition.cases[2].matchExpression.should.equal(ElseMatchExpression)
         (whenFieldSetCondition.cases[2].assignments.first().assignment as LiteralExpression)
            .value
            .should.equal("PartiallyFilled")
      }

      it("A join view with two types with single join fields can be compiled") {
         val src = """
         type OrderId inherits String
         type DummyType inherits Decimal
         model OrderSent {
            @Id
            sentOrderId : OrderId
            tempOrderId : OrderId
         }

         model OrderFill {
           @Id
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] (joinTo OrderFill[]) } as { id: DummyType }
         }
           """.trimIndent()
         val (compilationErrors, _) = Compiler(src).compileWithMessages()
         compilationErrors.errors().should.be.empty
      }

      it("find expressions in a view can only contain list types") {
         val src = """
         type OrderId inherits String
         model OrderSent {
            sentOrderId : OrderId
            tempOrderId : OrderId
         }

         model OrderFill {
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent (joinTo OrderFill[]) }
         }
           """.trimIndent()
         val (compilationErrors, _) = Compiler(src).compileWithMessages()
         compilationErrors.last().detailMessage.should.equal("Currently, only list types are supported in view definitions. Replace OrderSent with OrderSent[]")
      }

      it("find expressions in a view can only contain list types for joins") {
         val src = """
         type OrderId inherits String
         model OrderSent {
            sentOrderId : OrderId
            tempOrderId : OrderId
         }

         model OrderFill {
           fillOrderId: OrderId
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] (joinTo OrderFill) }
         }
           """.trimIndent()
         val (compilationErrors, _) = Compiler(src).compileWithMessages()
         compilationErrors.last().detailMessage.should.equal("Currently, only list types are supported in view definitions. Replace OrderFill with OrderFill[]")
      }


      it("view names must be unique") {
         val src = """
            namespace notional
            {
               type LastName inherits String
               model Person {
                  firstName : FirstName inherits String
                  lastName : LastName
               }
           }
           """.trimIndent()

         val viewSrc = """
            import notional.Person;
            import notional.LastName;
            namespace notional.views {
                [[
                 Sample View
                ]]
               view PersonView with query {
                  find { Person[] } as  {
                      surname: Person::LastName
                  }
               }
            }
         """.trimIndent()

         val anotherViewSrc = """
            import notional.Person;
            import notional.LastName;
            namespace notional.views2 {
                [[
                 Sample View
                ]]
               view PersonView with query {
                  find { Person[] } as  {
                      surname: Person::LastName
                  }
               }
            }
         """.trimIndent()
         val (error, _) = Compiler.forStrings(listOf(src, viewSrc, anotherViewSrc)).compileWithMessages()
         error.first().detailMessage.should.equal("view, name - PersonView must be unique")
      }

      it("multiple views  with unique names should be fine") {
         val src = """
            namespace notional
            {
               type LastName inherits String
               model Person {
                  firstName : FirstName inherits String
                  lastName : LastName
               }
           }
           """.trimIndent()

         val viewSrc = """
            import notional.Person;
            import notional.LastName;
            namespace notional.views {
                [[
                 Sample View
                ]]
               view PersonView with query {
                  find { Person[] } as  {
                      surname: Person::LastName
                  }
               }
            }
         """.trimIndent()

         val anotherViewSrc = """
            import notional.Person;
            import notional.LastName;
            namespace notional.views2 {
                [[
                 Sample View
                ]]
               view PersonView2 with query {
                  find { Person[] } as  {
                      surname: Person::LastName
                  }
               }

               [[
                 Sample View
                ]]
               view PersonView3 with query {
                  find { Person[] } as  {
                      surname: Person::LastName
                  }
               }
            }
         """.trimIndent()
         val (error, taxi) = Compiler.forStrings(listOf(src, viewSrc, anotherViewSrc)).compileWithMessages()
         error.should.be.empty
         taxi.views.size.should.equal(3)
      }

      it("a view with when statements using arithmetic operations") {
         val src = """
            namespace notional
            {
               type ExecutedQuantity inherits Decimal
               type OrderBuy inherits String
               type MarketTradeId inherits String
               type OrderSell inherits String
               type BuyCumulativeQuantity inherits Decimal
               type OrderId inherits String

               model OrderFilled {
                  orderId: OrderId
                  execQty: ExecutedQuantity
                  orderBuy: OrderBuy
                  marketId: MarketTradeId
                  orderSell: OrderSell
               }
           }
           """.trimIndent()

         val viewSrc = """
            import notional.ExecutedQuantity;
            import notional.OrderBuy;
            import notional.MarketTradeId;
            import notional.OrderSell;
            import notional.BuyCumulativeQuantity;
            import notional.OrderFilled;
            import notional.OrderId;
            import vyne.aggregations.sumOver;
            namespace notional.views {
               type CumulativeQty inherits Decimal
               type SellCumulativeQty inherits Decimal
               view ReportView with query {
                  find { OrderFilled[] } as  {
                       cumQty: CumulativeQty by sumOver(OrderFilled::ExecutedQuantity, OrderFilled::OrderId, OrderFilled::MarketTradeId)
                       sellCumulativeQty: SellCumulativeQty by sumOver(OrderFilled::ExecutedQuantity, OrderFilled::OrderSell, OrderFilled::MarketTradeId)
                       buyCumulativeQuantity: BuyCumulativeQuantity by when {
                        OrderFilled::OrderBuy != null  -> sumOver(OrderFilled::ExecutedQuantity, OrderFilled::OrderBuy, OrderFilled::MarketTradeId)
                        else -> (ReportView::CumulativeQty - ReportView::SellCumulativeQty)
                      }
                  }
               }
            }
         """.trimIndent()
         //sumOver(OrderFilled::ExecutedQuantity, OrderFilled::OrderId, OrderFilled::MarketTradeId) - sumOver(OrderFilled::ExecutedQuantity, OrderFilled::OrderSell, OrderFilled::MarketTradeId)
         val (error, taxi) = Compiler.forStrings(listOf(src, viewSrc)).compileWithMessages()
         error.should.be.empty
         taxi.views.size.should.equal(1)
      }
   }
})
