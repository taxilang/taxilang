package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.AndExpression
import lang.taxi.types.CalculatedModelAttributeFieldSetExpression
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperator
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.ConstantEntity
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EmptyReferenceSelector
import lang.taxi.types.FieldReferenceSelector
import lang.taxi.types.FormulaOperator
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.LiteralAssignment
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName
import lang.taxi.types.ModelAttributeTypeReferenceAssignment
import lang.taxi.types.ModelAttributeFieldReferenceEntity
import lang.taxi.types.ScalarAccessorValueAssignment
import lang.taxi.types.WhenCaseBlock
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
                 OrderSent::RequestedQuantity = OrderFill::DecimalFieldOrderFilled -> OrderFill::OrderStatus
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
         whenField.accessor.should.equal(ConditionalAccessor(WhenFieldSetCondition(selectorExpression = EmptyReferenceSelector,
            cases = listOf(
               WhenCaseBlock(
                  matchExpression = ComparisonExpression(operator = ComparisonOperator.EQ,
                     left = ModelAttributeFieldReferenceEntity(source = orderSentType.toQualifiedName(), fieldType = requestedQuantityType),
                     right = ModelAttributeFieldReferenceEntity(source = orderFillType.toQualifiedName(), fieldType = decimalFieldOrderFilled)),
                  assignments = listOf(InlineAssignmentExpression(assignment = ModelAttributeTypeReferenceAssignment(type = orderStatusType, source = orderFillType.toQualifiedName())))
               ),
               WhenCaseBlock(
                  matchExpression = ElseMatchExpression,
                  assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment("PartiallyFilled"))))))
         ))
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
              orderId: OrderSent::SentOrderId
              orderDateTime: OrderSent::OrderEventDateTime
              orderType: OrderSent::OrderType
              subSecurityType: OrderSent::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderSent::OrderStatus
              orderSize: OrderSize by when {
                 OrderSent::RequestedQuantity = 0 -> "Zero Size"
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
         whenField.type.qualifiedName.should.equal("OrderSize")
         whenField.accessor.should.equal(
            ConditionalAccessor(
               expression = WhenFieldSetCondition(
                  selectorExpression = EmptyReferenceSelector,
                  cases = listOf(
                     WhenCaseBlock(
                        matchExpression = ComparisonExpression(
                           operator = ComparisonOperator.EQ,
                           left = ModelAttributeFieldReferenceEntity(
                              source = orderSentType.toQualifiedName(),
                              fieldType = requestedQtyType),
                           right = ConstantEntity(0)),
                        assignments = listOf(InlineAssignmentExpression(LiteralAssignment("Zero Size")))),
                     WhenCaseBlock(
                        matchExpression = AndExpression(
                           left = ComparisonExpression(
                              operator = ComparisonOperator.GT,
                              left = ModelAttributeFieldReferenceEntity(source = orderSentType.toQualifiedName(), fieldType = requestedQtyType),
                              right = ConstantEntity(0)),
                           right = ComparisonExpression(
                              operator = ComparisonOperator.LT,
                              left = ModelAttributeFieldReferenceEntity(source = orderSentType.toQualifiedName(), fieldType = requestedQtyType),
                              right = ConstantEntity(100))),
                        assignments = listOf(InlineAssignmentExpression(LiteralAssignment("Small Size")))),
                     WhenCaseBlock(
                        matchExpression = ElseMatchExpression,
                        assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment("PartiallyFilled"))))
                  )))
         )

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

      it("A view with one find statement and aggregate field definition") {
         val src = """
            import vyne.aggregations.sumOver
            namespace taxi.test
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits Decimal
         type OrderStatus inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type OrderSize inherits String
         type CumulativeQty inherits Decimal
         type TradeNumber inherits Int
         type ExecutedQuantity inherits Decimal
         type MarketId inherits String
         type RemainingQuantity inherits Decimal
         type DisplayedQuantity inherits Decimal

         model OrderSent {
            @Id
            sentOrderId : SentOrderId
            @Between
		      orderDateTime: OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss") by column("Time Submitted")
            orderType: OrderType by default("Market")
            subSecurityType: SecurityDescription? by column("Instrument Desc")
            requestedQuantity: RequestedQuantity? by column("Size")
            entryType: OrderStatus by default("New")
            tradeNo: TradeNumber
            executedQty: ExecutedQuantity
            marketId: MarketId

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
              cumulativeQty: CumulativeQty by when {
                OrderSent::TradeNumber != null ->  sumOver(OrderSent::ExecutedQuantity, OrderSent::SentOrderId, OrderSent::MarketId)
                else -> OrderSent::ExecutedQuantity
               }

              leavesQuantity: RemainingQuantity by when {
                 OrderSent::RequestedQuantity = OrderSent::ExecutedQuantity -> 0
                 else -> (OrderSent::RequestedQuantity - OrderView::CumulativeQty)
               }

              displayQuantity: DisplayedQuantity by when {
                 OrderSent::RequestedQuantity = OrderSent::ExecutedQuantity -> 0
                 else -> OrderView::RemainingQuantity
               }
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val orderSentType = taxiDocument.model("taxi.test.OrderSent")
         fun testType(typeName: String) = taxiDocument.type("taxi.test.$typeName")
         orderSentType.should.not.be.`null`
         val orderView = taxiDocument.view("taxi.test.OrderView")
         orderView.should.not.be.`null`
         orderView?.typeDoc.should.equal("Sample View")
         orderView?.viewBodyDefinitions?.size.should.equal(1)
         val findOrderSent = orderView!!.viewBodyDefinitions!!.first()
         findOrderSent.bodyType.qualifiedName.should.equal("taxi.test.OrderSent")
         val bodyType = (findOrderSent.viewBodyType!! as ObjectType)
         bodyType.anonymous.should.be.`true`
         bodyType.field("orderId").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("orderDateTime").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("orderType").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("subSecurityType").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("requestedQuantity").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("orderEntry").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         val cumulativeQtyField = bodyType.field("cumulativeQty")
         cumulativeQtyField.memberSource.should.be.`null`
         val conditionalAccessor = cumulativeQtyField.accessor as ConditionalAccessor
         conditionalAccessor.expression.should.equal(
            WhenFieldSetCondition(selectorExpression = EmptyReferenceSelector,
               cases = listOf(
                  WhenCaseBlock(
                     matchExpression = ComparisonExpression(
                        operator = ComparisonOperator.NQ,
                        left = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("TradeNumber")),
                        right = ConstantEntity(value = null)),
                     assignments = listOf(
                        InlineAssignmentExpression(
                           assignment = ScalarAccessorValueAssignment(
                              accessor = FunctionAccessor(
                                 taxiDocument.function("vyne.aggregations.sumOver"),
                                 listOf(
                                    ModelAttributeReferenceSelector(memberSource = orderSentType.toQualifiedName(), memberType = testType("ExecutedQuantity")),
                                    ModelAttributeReferenceSelector(memberSource = orderSentType.toQualifiedName(), memberType = testType("SentOrderId")),
                                    ModelAttributeReferenceSelector(memberSource = orderSentType.toQualifiedName(), memberType = testType("MarketId"))
                                 )))))),
                  WhenCaseBlock(
                     matchExpression = ElseMatchExpression,
                     assignments = listOf(
                        InlineAssignmentExpression(
                           assignment = ModelAttributeTypeReferenceAssignment(
                              source = orderSentType.toQualifiedName(),
                              type = testType("ExecutedQuantity")))))
               ))
         )

         val leavesQtyField = bodyType.field("leavesQuantity")
         leavesQtyField.memberSource.should.be.`null`
         val leavesQtyFieldConditionalAccessor = leavesQtyField.accessor as ConditionalAccessor
         leavesQtyFieldConditionalAccessor.expression.should.equal(
            WhenFieldSetCondition(
               selectorExpression = EmptyReferenceSelector,
               cases = listOf(
                  WhenCaseBlock(
                     matchExpression = ComparisonExpression(
                        operator = ComparisonOperator.EQ,
                        left = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("RequestedQuantity")),
                        right = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("ExecutedQuantity"))
                     ),
                     assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment(0)))),
                  WhenCaseBlock(
                     matchExpression = ElseMatchExpression,
                     assignments = listOf(
                        InlineAssignmentExpression(
                           ScalarAccessorValueAssignment(
                              accessor = ConditionalAccessor(
                                 expression = CalculatedModelAttributeFieldSetExpression(
                                    operand1 = ModelAttributeReferenceSelector(
                                       memberSource = orderSentType.toQualifiedName(),
                                       memberType = testType("RequestedQuantity")),
                                    operand2 = ModelAttributeReferenceSelector(
                                       memberSource = orderView.toQualifiedName(),
                                       memberType = testType("CumulativeQty")),
                                    operator = FormulaOperator.Subtract
                                 )
                              )
                           )
                        )
                     )
                  )
               )
            )
         )

         val displayQuantity = bodyType.field("displayQuantity")
         val displayQuantityFieldConditionalAccessor = displayQuantity.accessor as ConditionalAccessor
         displayQuantityFieldConditionalAccessor.expression.should.equal(
            WhenFieldSetCondition(
               selectorExpression = EmptyReferenceSelector,
               cases = listOf(
                  WhenCaseBlock(
                     matchExpression = ComparisonExpression(
                        operator = ComparisonOperator.EQ,
                        left = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("RequestedQuantity")),
                        right = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("ExecutedQuantity"))
                     ),
                     assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment(0)))),
                  WhenCaseBlock(
                     matchExpression = ElseMatchExpression,
                     assignments = listOf(
                        InlineAssignmentExpression(
                           ModelAttributeTypeReferenceAssignment(
                              source = orderView.toQualifiedName(),
                              type = testType("RemainingQuantity")
                           )
                        )
                     )
                  )
               )
            )
         )
      }

      it("A view one join statement and aggregate field definition") {
         val src = """
         type GroupId inherits String
         type GroupName inherits String
         type ProductName inherits String
         type Price inherits Decimal

         declare query function avgOver(Decimal,String):Decimal

         model ProductGroup {
            @Id
            id: GroupId
            name: GroupName
         }

         model Product {
            @Id
            groupId: GroupId
            name: ProductName
            price: Price
         }

          [[
           Sample View
          ]]
         view ProductView with query {
            find { Product[] (joinTo ProductGroup[]) } as {
               productName: Product::ProductName
               price: Product::Price
               groupName: ProductGroup::GroupName
               avgGroupPrice: Price by avgOver(Product::Price, ProductGroup::GroupName)
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val productView = taxiDocument.view("ProductView")
         productView.should.not.be.`null`
         productView?.typeDoc.should.equal("Sample View")
         productView?.viewBodyDefinitions?.size.should.equal(1)
         val findOrderSent = productView!!.viewBodyDefinitions!!.first()
         findOrderSent.bodyType.qualifiedName.should.equal("Product")
         val bodyType = (findOrderSent.viewBodyType!! as ObjectType)
         bodyType.anonymous.should.be.`true`
         bodyType.field("productName").memberSource?.fullyQualifiedName.should.equal("Product")
         bodyType.field("price").memberSource?.fullyQualifiedName.should.equal("Product")
         bodyType.field("groupName").memberSource?.fullyQualifiedName.should.equal("ProductGroup")
         val avgGroupPriceField = bodyType.field("avgGroupPrice")
         avgGroupPriceField.memberSource.should.be.`null`
         val avgGroupAccessor = avgGroupPriceField.accessor as FunctionAccessor
         avgGroupAccessor.function.qualifiedName.should.equal("avgOver")
         val firstArgument = avgGroupAccessor.inputs.first() as ModelAttributeReferenceSelector
         firstArgument.memberSource.fullyQualifiedName.should.equal("Product")
         val secondArgument = avgGroupAccessor.inputs[1] as ModelAttributeReferenceSelector
         secondArgument.memberSource.fullyQualifiedName.should.equal("ProductGroup")
      }

      it("only model attribute based function accessors are allowed within views") {
         val src = """

         declare function avgOver(Decimal,String):Decimal

         model ProductGroup {
            @Id
            id: GroupId as String
            name: GroupName as String
         }

         model Product {
            @Id
            groupId: GroupId as String
            name: ProductName as String
            price: Price as String
         }

          [[
           Sample View
          ]]
         view ProductView with query {
            find { Product[] (joinTo ProductGroup[]) } as {
               productName: Product::ProductName
               price: Product::Price
               groupName: ProductGroup::GroupName
               avgGroupPrice: Price by avgOver(this.price, this.groupName)
            }
         }
           """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.last().detailMessage.should.equal("Only Model Attribute References are  allowed within Views")
      }

      it("should detect references to types that are not part of the view definition") {
         val src = """
         import vyne.aggregations.sumOver
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits String
         type OrderStatus inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type AggregatedCumulativeQty inherits Decimal
         type TradeNo inherits String
         type CumulativeQuantity inherits Decimal
         type RemainingQuantity inherits Decimal
         type DisplayedQuantity inherits Decimal


         model OrderSent {
            @Id
            sentOrderId : SentOrderId
            @Between
		      orderDateTime: OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss") by column("Time Submitted")
            orderType: OrderType by default("Market")
            subSecurityType: SecurityDescription? by column("Instrument Desc")
            requestedQuantity: RequestedQuantity? by column("Size")
            remainingQuantity: RemainingQuantity? by column("Size")
            displayedQuantity: DisplayedQuantity? by column("Size")
            entryType: OrderStatus by default("New")
         }

         model OrderFill {
           @Id
           fillOrderId: FillOrderId
           orderType: OrderType by default("Market")
           subSecurityType: SecurityDescription? by column("Instrument Desc")
           executedQuantity: DecimalFieldOrderFilled? by column("Quantity")
           entryType: OrderStatus by default("Filled")
           tradeNo: TradeNo by column("TradeNo")
         }

         model OrderEvent { }

          [[
           Sample View
          ]]
         view OrderView inherits OrderEvent with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              orderDateTime: OrderSent::OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss")
              orderType: OrderSent::OrderType
              subSecurityType: OrderSent::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderSent::OrderStatus
              leavesQuantity: OrderSent::RemainingQuantity
              displayQuantity: OrderSent::DisplayedQuantity
              tradeNo: TradeNo
              executedQuantity: DecimalFieldOrderFilled
              cumulativeQty: CumulativeQuantity
            },
            find { OrderSent[] (joinTo OrderFill[]) } as {
              orderId: OrderFill::FillOrderId
              orderDateTime: OrderEventDateTime
              orderType: OrderFill::OrderType
              subSecurityType: OrderFill::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderStatus by when {
                 OrderSent::RequestedQuantity = OrderFill::DecimalFieldOrderFilled -> OrderFill::OrderStatus
                 else -> "PartiallyFilled"
              }
              leavesQuantity: RemainingQuantity by when {
                    OrderSent::RequestedQuantity = OrderFilled::DecimalFieldOrderFilled -> 0
                    else -> (OrderSent::RequestedQuantity - OrderView::CumulativeQuantity)
              }
              displayQuantity: DisplayedQuantity by when {
                  OrderSent::RequestedQuantity = OrderFilled::DecimalFieldOrderFilled -> 0
                  else -> OrderView::RemainingQuantity
              }
              tradeNo: OrderFill::TradeNo
              executedQuantity: OrderFill::DecimalFieldOrderFilled
              cumulativeQty: CumulativeQuantity by when {
                  OrderFill::TradeNo = null -> OrderFill::DecimalFieldOrderFilled
                  else -> sumOver(OrderFill::DecimalFieldOrderFilled, OrderFill::FillOrderId, OrderFill::TradeNo)
                }
            }
         }
   """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("A Reference to OrderFilled is invalid in this view definition context")
         errors.last().detailMessage.should.equal("OrderFilled::DecimalFieldOrderFilled is invalid as OrderFilled does not have a field with type DecimalFieldOrderFilled")
      }

      it("should detect view field references on the left hand side of when statements") {
         val src = """
         import vyne.aggregations.sumOver
         type SentOrderId inherits String
         type FillOrderId inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type CumulativeQuantity inherits Decimal



         model OrderSent {
            @Id
            sentOrderId : SentOrderId
         }

         model OrderFill {
           @Id
           fillOrderId: FillOrderId
           executedQuantity: DecimalFieldOrderFilled? by column("Quantity")
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              executedQuantity: DecimalFieldOrderFilled
              cumulativeQty: CumulativeQuantity
            },
            find { OrderSent[] (joinTo OrderFill[]) } as {
              orderId: OrderFill::FillOrderId
              executedQuantity: DecimalFieldOrderFilled by when {
                 OrderView::CumulativeQuantity = null -> 0
                 else -> OrderView::CumulativeQuantity
              }
              cumulativeQty: CumulativeQuantity
            }
         }
   """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("Invalid context for OrderView::CumulativeQuantity. You can not use a reference to View on the left hand side of a case when expression.")
        }
   }
})
