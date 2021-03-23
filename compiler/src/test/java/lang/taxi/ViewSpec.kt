package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperator
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EmptyReferenceSelector
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.LiteralAssignment
import lang.taxi.types.ViewBodyFieldDefinition
import lang.taxi.types.ViewFindFieldReferenceAssignment
import lang.taxi.types.ViewFindFieldReferenceEntity
import lang.taxi.types.WhenCaseBlock
import lang.taxi.types.WhenFieldSetCondition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ViewSpec : Spek({
   describe("view syntax") {
      it("simple view definition") {
         val src = """
         model Person {
            firstName : FirstName as String
            lastName : LastName as String
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] }
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
               model Person {
                  firstName : FirstName as String
                  lastName : LastName as String
               }
           }
           """.trimIndent()

         val viewSrc = """
            namespace notional.views {
                [[
                 Sample View
                ]]
               view PersonView with query {
                  find { notional.Person[] }
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
            find { OrderSent[] },
            find { OrderSent[] (joinTo OrderFill[]) }
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
         compilationErrors.last().detailMessage.should.equal("OrderSent and OrderFill can't be joined")
      }

      it("multiple Find Statements in a View must be compatible with each other") {
         val src = """
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits String
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
              orderId: OrderSent.SentOrderId
              orderDateTime: OrderSent.OrderEventDateTime
              orderType: OrderSent.OrderType
              subSecurityType: OrderSent.SecurityDescription
              requestedQuantity: OrderSent.RequestedQuantity
              orderEntry: OrderSent.OrderStatus
            },
            find { OrderSent[] (joinTo OrderFill[]) } as {
              orderId: OrderFill.FillOrderId
              orderDateTime: OrderEventDateTime
              orderType: OrderFill.OrderType
              subSecurityType: OrderFill.SecurityDescription
              requestedQuantity: OrderSent.RequestedQuantity
              orderEntry: OrderStatus by when {
                 OrderSent.RequestedQuantity = OrderFill.DecimalFieldOrderFilled -> OrderFill.OrderStatus
                 else -> "PartiallyFilled"
              }
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val orderFillType = taxiDocument.model("OrderFill")
         val orderSentType = taxiDocument.model("OrderSent")
         val orderEventDateTime = taxiDocument.type("OrderEventDateTime")
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
         findOrderSent.viewBodyTypeDefinition!!.fields.should.equal(
            listOf(
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("sentOrderId").type, fieldName = "orderId"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("orderDateTime").type, fieldName = "orderDateTime"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("orderType").type, fieldName = "orderType"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("subSecurityType").type, fieldName = "subSecurityType"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("requestedQuantity").type, fieldName = "requestedQuantity"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("entryType").type, fieldName = "orderEntry")
            ))
         joinOrderSentOrderFill.bodyType.qualifiedName.should.equal("OrderSent")
         joinOrderSentOrderFill.joinType!!.qualifiedName.should.equal("OrderFill")
         joinOrderSentOrderFill.viewBodyTypeDefinition!!.fields.should.equal(
            listOf(
               ViewBodyFieldDefinition(sourceType = orderFillType, fieldType = orderFillType.field("fillOrderId").type, fieldName = "orderId"),
               ViewBodyFieldDefinition(sourceType = orderEventDateTime, fieldType = orderEventDateTime, fieldName = "orderDateTime"),
               ViewBodyFieldDefinition(sourceType = orderFillType, fieldType = orderFillType.field("orderType").type, fieldName = "orderType"),
               ViewBodyFieldDefinition(sourceType = orderFillType, fieldType = orderFillType.field("subSecurityType").type, fieldName = "subSecurityType"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("requestedQuantity").type, fieldName = "requestedQuantity"),
               ViewBodyFieldDefinition(sourceType = orderStatusType, fieldType = orderStatusType, fieldName = "orderEntry",
                  accessor = ConditionalAccessor(WhenFieldSetCondition(selectorExpression = EmptyReferenceSelector,
                     cases = listOf(
                        WhenCaseBlock(
                           matchExpression = ComparisonExpression(operator = ComparisonOperator.EQ,
                              left = ViewFindFieldReferenceEntity(sourceType = orderSentType, fieldType = requestedQuantityType),
                              right = ViewFindFieldReferenceEntity(sourceType = orderFillType, fieldType = decimalFieldOrderFilled)),
                           assignments = listOf(InlineAssignmentExpression(assignment = ViewFindFieldReferenceAssignment(fieldType = orderFillType, type = orderStatusType)))
                        ),
                        WhenCaseBlock(
                           matchExpression = ElseMatchExpression,
                           assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment("PartiallyFilled"))))))
                  ))))


         val fieldWithWhenCases = joinOrderSentOrderFill.viewBodyTypeDefinition!!.fields.first { it.fieldName == "orderEntry" }
         fieldWithWhenCases.accessor.should.not.be.`null`
      }

      it("A join view with two types with single join fields can be compiled") {
         val src = """
         type OrderId inherits String
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
            find { OrderSent[] (joinTo OrderFill[]) }
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
   }
})
