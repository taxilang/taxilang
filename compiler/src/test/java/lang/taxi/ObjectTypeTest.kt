package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import lang.taxi.types.ObjectType
import org.mockito.kotlin.times

class ObjectTypeTest : DescribeSpec({
   describe("object types") {
      // ORB-1091
      it("can call referenced types without excessive object allocation") {
         // Intentionally using a schema with lots of "stuff" in it,
         // as this is a test about memory leakage
         val schema = """
            namespace ecommerce

            type CustomerId inherits String
            type OrderId inherits String
            type ProductId inherits String
            type EmailAddress inherits String
            type FirstName inherits String
            type LastName inherits String
            type OrderStatus inherits String
            type OrderDate inherits Date
            type ProductName inherits String
            type ProductCategory inherits String
            type UnitPrice inherits Decimal
            type Quantity inherits Int
            type ReviewScore inherits Int
            type ReviewText inherits String

            model Customer {
               @Id
               id : CustomerId
               firstName : FirstName
               lastName : LastName
               email : EmailAddress
            }

            model Product {
               @Id
               id : ProductId
               name : ProductName
               category : ProductCategory
               price : UnitPrice
            }

            model OrderItem {
               productId : ProductId
               quantity : Quantity
               unitPrice : UnitPrice
            }

            model Order {
               @Id
               id : OrderId
               customerId : CustomerId
               status : OrderStatus
               orderDate : OrderDate
               items : OrderItem[]
            }

            model ProductReview {
               productId : ProductId
               score : ReviewScore
               reviewText : ReviewText
            }

            service CustomerService {
               operation getCustomers() : Customer[]
               operation getCustomer(CustomerId) : Customer
            }

            service OrderService {
               operation getOrders() : Order[]
               operation getOrdersByCustomer(CustomerId) : Order[]
               operation getOrder(OrderId) : Order
            }

            service ProductService {
               operation getProducts() : Product[]
               operation getProduct(ProductId) : Product
            }

            service ReviewService {
               operation getReviews(ProductId) : ProductReview[]
            }

         """.compiled()

         val objectTypes = schema.types
            .filter { !it.isScalar }
            .filterIsInstance<ObjectType>()

         fun fetchAllReferencedTypes() {
            objectTypes
               .forEach { it.referencedTypes }
         }

         // grab all the referenced types once
//         fetchAllReferencedTypes()

         // now do it 100 times. This is useful for monitoring heap
//         while(true) {
//            fetchAllReferencedTypes()
//         }

         // This test isn't super helpful in a test suite, but we had a huge alloacation
         // problem, and this is how I tracked it down.
         repeat(10_000) { fetchAllReferencedTypes() }
//

      }
   }

})
