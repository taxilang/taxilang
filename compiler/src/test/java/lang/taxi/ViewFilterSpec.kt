package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.services.operations.constraints.ConstantValueExpression
import lang.taxi.services.operations.constraints.PropertyToParameterConstraint
import lang.taxi.services.operations.constraints.PropertyTypeIdentifier
import lang.taxi.types.AndFilterExpression
import lang.taxi.types.FilterExpressionInParenthesis
import lang.taxi.types.InFilterExpression
import lang.taxi.types.LikeFilterExpression
import lang.taxi.types.OrFilterExpression
import lang.taxi.types.QualifiedName
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ViewFilterSpec : DescribeSpec({
   // Ignored while views are up for debate as a feature we want to keep.
   // Lets decide if we want to keep views before fixing this implementation
   xdescribe("view syntax with constraints / filters") {
      it("simple view definition with constraints") {
         val src = """
         model Person {
            firstName : FirstName inherits String
            lastName : LastName inherits String
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( FirstName == 'foo' and LastName like '%bar%' ) } as {
               personName: Person::FirstName
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         taxiDocument.model("Person").should.not.be.`null`
         val personView = taxiDocument.view("PersonView")
         personView.should.not.be.`null`
         personView?.typeDoc.should.equal("Sample View")
         val filter = personView?.viewBodyDefinitions?.first()?.bodyTypeFilter
         filter.should.not.be.`null`
         val andFilterExpression = filter as AndFilterExpression
         val left = andFilterExpression.filterLeft as PropertyToParameterConstraint
         val propertyTypeIdentifier = left.propertyIdentifier as PropertyTypeIdentifier
         propertyTypeIdentifier.type.equals(QualifiedName.from("FirstName"))
         val constantValueExpression = left.expectedValue as ConstantValueExpression
         constantValueExpression.value.should.equal("foo")
         val right = andFilterExpression.filterRight as LikeFilterExpression
         right.type.should.equal(taxiDocument.type("LastName"))
         right.value.should.equal("%bar%")
      }

      it("simple view definition with in constraints") {
         val src = """
         model Person {
            firstName : FirstName inherits String
            lastName : LastName inherits String
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( FirstName in ['marty', 'anthony']) } as {
               personName: Person::FirstName
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         taxiDocument.model("Person").should.not.be.`null`
         val personView = taxiDocument.view("PersonView")
         personView.should.not.be.`null`
         personView?.typeDoc.should.equal("Sample View")
         val filter = personView?.viewBodyDefinitions?.first()?.bodyTypeFilter
         filter.should.not.be.`null`
         val inFilterExpression = filter as InFilterExpression
         inFilterExpression.type.should.equal(taxiDocument.type("FirstName"))
         inFilterExpression.values.should.equal(listOf("marty", "anthony"))
      }

      it("simple view definition with or and constraints") {
         val src = """
         model Person {
            firstName : FirstName inherits String
            lastName : LastName inherits String
            age: Age inherits Int
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( (FirstName == 'foo' or LastName like '%bar%') and (Age in [20, 21, 22]) ) } as {
               personName: Person::FirstName
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         taxiDocument.model("Person").should.not.be.`null`
         val personView = taxiDocument.view("PersonView")
         personView.should.not.be.`null`
         personView?.typeDoc.should.equal("Sample View")
         val filter = personView?.viewBodyDefinitions?.first()?.bodyTypeFilter
         filter.should.not.be.`null`
         val andFilterExpression = filter as AndFilterExpression
         val left = andFilterExpression.filterLeft as FilterExpressionInParenthesis
         val or = left.containedExpression as OrFilterExpression

         val leftOfOr = or.filterLeft as PropertyToParameterConstraint
         val propertyTypeIdentifier = leftOfOr.propertyIdentifier as PropertyTypeIdentifier
         propertyTypeIdentifier.type.equals(QualifiedName.from("FirstName"))
         val constantValueExpression = leftOfOr.expectedValue as ConstantValueExpression
         constantValueExpression.value.should.equal("foo")

         val rightOfOr = or.filterRight as LikeFilterExpression
         rightOfOr.type.should.equal(taxiDocument.type("LastName"))
         rightOfOr.value.should.equal("%bar%")

         val right = andFilterExpression.filterRight as FilterExpressionInParenthesis
         val rightInParanthesis = right.containedExpression as InFilterExpression
         rightInParanthesis.type.should.equal(taxiDocument.type("Age"))
         rightInParanthesis.values.should.equal(listOf(20, 21, 22))
      }

      it ("A PropertyToParameterConstraint can not reference an invalid type") {
         val src = """
         type LastName inherits String
         model Person {
            firstName : FirstName inherits String
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( (FirstName == 'foo' or LastName like '%bar%') ) } as {
               personName: Person::FirstName
            }
         }
           """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("Person does not have a field with type LastName")
      }

      it ("like filter is only applicable to String fields") {
         val src = """
         model Person {
            age : Age inherits Int
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( Age like '%bar%') } as {
               age: Person::Age
            }
         }
           """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("Age must be a lang.taxi.String type")
      }

      it ("in filter can not have a mixed match list") {
         val src = """
         model Person {
            age : Age inherits Int
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( Age in [20, 'adult']) } as {
               age: Person::Age
            }
         }
           """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("arguments of in must be of same type 20 is not compatible with adult")
      }

      it ("in filter should have a compatible match list with match Type") {
         val src = """
         model Person {
            age : Age inherits Int
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( Age in ['boomer', 'generationZ']) } as {
               age: Person::Age
            }
         }
           """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("boomer is incorrect, it must be numeric")
      }

      it("A view with two find statements with filter expressions") {
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
            find { OrderSent[] ( (SecurityDescription == 'Floating' or SecurityDescription =='Fixed') and OrderType in ['Limit', 'Market']) } as {
              orderId: OrderSent::SentOrderId
              orderDateTime: OrderSent::OrderEventDateTime
              orderType: OrderSent::OrderType
              subSecurityType: OrderSent::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderSent::OrderStatus
            },
            find { OrderSent[] ( (SecurityDescription == 'Floating') ) (joinTo OrderFill[] ( OrderStatus == 'Filled' or OrderStatus == 'Partially Filled' )) } as {
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
         val orderView = taxiDocument.view("OrderView")
         val findOrderSent = orderView!!.viewBodyDefinitions!!.first()
         val joinOrderSentOrderFill = orderView.viewBodyDefinitions!![1]
         findOrderSent.bodyTypeFilter.should.not.be.`null`
         findOrderSent.joinTypeFilter.should.be.`null`
         joinOrderSentOrderFill.bodyTypeFilter.should.not.be.`null`
         joinOrderSentOrderFill.joinTypeFilter.should.not.be.`null`
         // (SecurityDescription = 'Floating' or SecurityDescription = 'Fixed') and OrderType in ['Limit', 'Market']
         val and = findOrderSent.bodyTypeFilter as AndFilterExpression
         val andRight = and.filterLeft as FilterExpressionInParenthesis
         val orFilter = andRight.containedExpression as OrFilterExpression
         ((orFilter.filterLeft as PropertyToParameterConstraint).propertyIdentifier as PropertyTypeIdentifier)
            .type
            .should
            .equal(QualifiedName.from("SecurityDescription"))
         ((orFilter.filterLeft as PropertyToParameterConstraint).expectedValue as ConstantValueExpression)
            .value
            .should
            .equal("Floating")
         ((orFilter.filterRight as PropertyToParameterConstraint).propertyIdentifier as PropertyTypeIdentifier)
            .type
            .should
            .equal(QualifiedName.from("SecurityDescription"))
         ((orFilter.filterRight as PropertyToParameterConstraint).expectedValue as ConstantValueExpression)
            .value
            .should
            .equal("Fixed")
         (and.filterRight as InFilterExpression).type.should.equal(taxiDocument.type("OrderType"))
         (and.filterRight as InFilterExpression).values.should.equal(listOf("Limit", "Market"))

         //(SecurityDescription = 'Floating')
         (((joinOrderSentOrderFill.bodyTypeFilter as FilterExpressionInParenthesis)
            .containedExpression as PropertyToParameterConstraint)
            .propertyIdentifier as PropertyTypeIdentifier)
            .type
            .should
            .equal(QualifiedName.from("SecurityDescription"))
         // OrderStatus = 'Filled' or OrderStatus = 'Partially Filled'
         (((joinOrderSentOrderFill.joinTypeFilter as OrFilterExpression)
            .filterLeft as PropertyToParameterConstraint)
            .propertyIdentifier as PropertyTypeIdentifier)
            .type
            .should
            .equal(QualifiedName.from("OrderStatus"))

         (((joinOrderSentOrderFill.joinTypeFilter as OrFilterExpression)
            .filterRight as PropertyToParameterConstraint)
            .propertyIdentifier as PropertyTypeIdentifier)
            .type
            .should
            .equal(QualifiedName.from("OrderStatus"))

      }

      it ("You can't use < with Strings") {
         val src = """
         model Person {
            name : Name inherits String
         }

          [[
           Sample View
          ]]
         view PersonView with query {
            find { Person[] ( (Name < 'joe') )} as {
               name: Person::Name
            }
         }
           """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("< is not applicable for String based types. Use only = or !=")
      }

      it ("enums are valid in constraints") {
         val src = """
         enum Sex {
            Male,
            Female
         }
         model Person {
            direction : Sex
         }

          [[
           Sample View
          ]]
         view MaleView with query {
            find { Person[] ( (Sex == 'Male') )} as {
               sex: Person::Sex
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         taxiDocument.model("Person").should.not.be.`null`
         val maleView = taxiDocument.view("MaleView")
         val filter = maleView?.viewBodyDefinitions?.first()?.bodyTypeFilter
         filter.should.not.be.`null`
      }
   }
})
