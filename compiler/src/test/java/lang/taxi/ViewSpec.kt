package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.AndExpression
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperator
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.ConstantEntity
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
            find { Person[] } as {
               personName: Person.FirstName
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
                  firstName : FirstName as String
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
                      surname: Person.LastName
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
            find { OrderSent[] } as { id: OrderSent.OrderId },
            find { OrderSent[] (joinTo OrderFill[]) } as { id: OrderSent.OrderId }
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
            find { OrderSent[] } as { id: ModelFoo.OrderId },
            find { OrderSent[] (joinTo OrderFill[]) } as { id: OrderSent.OrderId}
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
              id: OrderSent.OrderId
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
                           assignments = listOf(InlineAssignmentExpression(assignment = ViewFindFieldReferenceAssignment(fieldType = orderStatusType, type = orderFillType)))
                        ),
                        WhenCaseBlock(
                           matchExpression = ElseMatchExpression,
                           assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment("PartiallyFilled"))))))
                  ))))


         val fieldWithWhenCases = joinOrderSentOrderFill.viewBodyTypeDefinition!!.fields.first { it.fieldName == "orderEntry" }
         fieldWithWhenCases.accessor.should.not.be.`null`
      }

      it("A view with one find statement") {
         val src = """
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits String
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
              orderId: OrderSent.SentOrderId
              orderDateTime: OrderSent.OrderEventDateTime
              orderType: OrderSent.OrderType
              subSecurityType: OrderSent.SecurityDescription
              requestedQuantity: OrderSent.RequestedQuantity
              orderEntry: OrderSent.OrderStatus
              orderSize: OrderSize by when {
                 OrderSent.RequestedQuantity = 0 -> "Zero Size"
                 OrderSent.RequestedQuantity > 0 && OrderSent.RequestedQuantity < 100 -> "Small Size"
                 else -> "PartiallyFilled"
              }
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val orderSentType = taxiDocument.model("OrderSent")
         val orderSizeType = taxiDocument.type("OrderSize")
         val requestedQtyType = taxiDocument.type(("RequestedQuantity"))
         orderSentType.should.not.be.`null`
         val orderView = taxiDocument.view("OrderView")
         orderView.should.not.be.`null`
         orderView?.typeDoc.should.equal("Sample View")
         orderView?.viewBodyDefinitions?.size.should.equal(1)
         val findOrderSent = orderView!!.viewBodyDefinitions!!.first()
         findOrderSent.bodyType.qualifiedName.should.equal("OrderSent")
         findOrderSent.viewBodyTypeDefinition!!.fields.should.equal(
            listOf(
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("sentOrderId").type, fieldName = "orderId"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("orderDateTime").type, fieldName = "orderDateTime"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("orderType").type, fieldName = "orderType"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("subSecurityType").type, fieldName = "subSecurityType"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("requestedQuantity").type, fieldName = "requestedQuantity"),
               ViewBodyFieldDefinition(sourceType = orderSentType, fieldType = orderSentType.field("entryType").type, fieldName = "orderEntry"),
               ViewBodyFieldDefinition(
                  sourceType = orderSizeType,
                  fieldType = orderSizeType,
                  fieldName = "orderSize",
                  accessor = ConditionalAccessor(
                     expression = WhenFieldSetCondition(
                        selectorExpression = EmptyReferenceSelector,
                        cases = listOf(
                           WhenCaseBlock(
                              matchExpression = ComparisonExpression(
                                 operator = ComparisonOperator.EQ,
                                 left = ViewFindFieldReferenceEntity(
                                    sourceType = orderSentType,
                                    fieldType = requestedQtyType),
                                 right = ConstantEntity(0)),
                              assignments = listOf(InlineAssignmentExpression(LiteralAssignment("Zero Size")))),
                           WhenCaseBlock(
                              matchExpression = AndExpression(
                                 left = ComparisonExpression(
                                    operator = ComparisonOperator.GT,
                                    left = ViewFindFieldReferenceEntity(sourceType = orderSentType, fieldType = requestedQtyType),
                                    right = ConstantEntity(0)),
                                 right = ComparisonExpression(
                                    operator = ComparisonOperator.LT,
                                    left = ViewFindFieldReferenceEntity(sourceType = orderSentType, fieldType = requestedQtyType),
                                    right = ConstantEntity(100))),
                              assignments = listOf(InlineAssignmentExpression(LiteralAssignment("Small Size")))),
                           WhenCaseBlock(
                              matchExpression = ElseMatchExpression,
                              assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment("PartiallyFilled"))))
                        ))))
            ))
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
   }
})
