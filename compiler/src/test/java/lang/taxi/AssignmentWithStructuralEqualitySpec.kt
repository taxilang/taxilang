package lang.taxi

import arrow.core.Either
import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class AssignmentWithStructuralEqualitySpec : DescribeSpec({

   describe("structural compatibility") {

      describe("scalar field compatibility") {
         it("should use nominal assignability for scalar fields") {
            val source = """
               type PersonName inherits String
               type DogName inherits String

               model Person {
                  name: PersonName
               }

               model NotAPerson {
                  name: DogName
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val notAPerson = schema.model("NotAPerson")

            // DogName is not assignable to PersonName, so structural compatibility fails
            notAPerson.isAssignableOrErrors(person).shouldNotBeAssignable("NotAPerson is not structurally compatible with Person because Field 'name' is has incompatible types of DogName and PersonName")
         }

         it("should allow structural compatibility when scalar fields are nominally compatible") {
            val source = """
               type Name inherits String
               type PersonName inherits Name

               model Person {
                  name: Name
               }

               model SpecificPerson {
                  name: PersonName
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val specificPerson = schema.model("SpecificPerson")

            // PersonName is assignable to Name, so structural compatibility succeeds
            specificPerson.isAssignableOrErrors(person).shouldBeAssignable()
         }

         it("should allow duck typing with extra scalar fields") {
            val source = """
               type FirstName inherits String
               type LastName inherits String
               type Age inherits Int

               model Person {
                  firstName: FirstName
                  lastName: LastName
               }

               model EnrichedPerson {
                  firstName: FirstName
                  lastName: LastName
                  age: Age
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val enrichedPerson = schema.model("EnrichedPerson")

            // EnrichedPerson has all fields Person requires (plus extras)
            enrichedPerson.isAssignableOrErrors(person).shouldBeAssignable()
         }

         it("should fail when required scalar field is missing") {
            val source = """
               type FirstName inherits String
               type LastName inherits String

               model Person {
                  firstName: FirstName
                  lastName: LastName
               }

               model PartialPerson {
                  firstName: FirstName
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val partialPerson = schema.model("PartialPerson")

            // PartialPerson is missing lastName
            partialPerson.isAssignableOrErrors(person).shouldNotBeAssignable("PartialPerson is not structurally compatible with Person because Missing required field 'lastName': LastName'")
         }
      }

      describe("object field compatibility") {
         it("should use recursive structural checking for object fields") {
            val source = """
               type Street inherits String
               type City inherits String
               type Postcode inherits String

               model Address {
                  street: Street
                  city: City
               }

               model Person {
                  address: Address
               }

               model EnrichedPerson {
                  address: {
                     street: Street
                     city: City
                     postcode: Postcode
                  }
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val enrichedPerson = schema.model("EnrichedPerson")

            // EnrichedPerson.address has all fields Address requires (plus postcode)
            enrichedPerson.isAssignableOrErrors(person).shouldBeAssignable()
         }

         it("should fail when nested object is missing required field") {
            val source = """
               type Street inherits String
               type City inherits String

               model Address {
                  street: Street
                  city: City
               }

               model Person {
                  address: Address
               }

               model IncompletePerson {
                  address: {
                     street: Street
                  }
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val incompletePerson = schema.model("IncompletePerson")

            // IncompletePerson.address is missing city
            incompletePerson.isAssignableOrErrors(person).shouldNotBeAssignable("IncompletePerson is not structurally compatible with Person because Field 'address' is has incompatible types of IncompletePerson\$Address and Address")
         }

         it("should handle deeply nested structural compatibility") {
            val source = """
               type Email inherits String
               type Phone inherits String
               type Fax inherits String
               type Street inherits String
               type Country inherits String

               model Contact {
                  email: Email
                  phone: Phone
               }

               model Address {
                  street: Street
                  contact: Contact
               }

               model Person {
                  address: Address
               }

               model EnrichedPerson {
                  address: {
                     street: Street
                     contact: {
                        email: Email
                        phone: Phone
                        fax: Fax
                     }
                     country: Country
                  }
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val enrichedPerson = schema.model("EnrichedPerson")

            // Structural compatibility at multiple nesting levels
            enrichedPerson.isAssignableOrErrors(person).shouldBeAssignable()
         }
      }

      describe("array field compatibility") {

         // This is the root cause of ORB-1060
         it("should not permit incompatible array objects to be assignable") {
            val schema = """
               model Order {
                  orderId : OrderId inherits Int
               }
               model OrderWithItems {
                  orderId : OrderId
                  items : {
                     name : ProductName inherits String
                  }[]
               }
            """.compiled()
            val orderArray = schema.type("Order[]")
            val orderWithItemsArray = schema.type("OrderWithItems[]")
            orderArray.isAssignableTo(orderWithItemsArray).shouldBeFalse()
            orderArray.isAssignableTo(orderWithItemsArray, permitStructurallyCompatible = false).shouldBeFalse()
            orderWithItemsArray.isAssignableTo(orderArray).shouldBeTrue()
            orderWithItemsArray.isAssignableTo(orderArray, permitStructurallyCompatible = false).shouldBeFalse()
            orderArray.isAssignableTo(orderArray).shouldBeTrue()
            orderWithItemsArray.isAssignableTo(orderWithItemsArray).shouldBeTrue()
         }

         it("should check array element types recursively") {
            val source = """
               type Street inherits String
               type City inherits String
               type Postcode inherits String

               model Address {
                  street: Street
                  city: City
               }

               model Person {
                  addresses: Address[]
               }

               model EnrichedPerson {
                  addresses: {
                     street: Street
                     city: City
                     postcode: Postcode
                  }[]
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val enrichedPerson = schema.model("EnrichedPerson")

            // Array element types are structurally compatible
            enrichedPerson.isAssignableOrErrors(person).shouldBeAssignable()
         }

         it("should fail when array element types are not compatible") {
            val source = """
               type PersonName inherits String
               type DogName inherits String

               model Person {
                  names: PersonName[]
               }

               model Dogs {
                  names: DogName[]
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val dogs = schema.model("Dogs")

            // DogName[] is not compatible with PersonName[]
            dogs.isAssignableOrErrors(person).shouldNotBeAssignable("Dogs is not structurally compatible with Person because Field 'names' is has incompatible types of DogName[] and PersonName[]")
         }
      }

      describe("nominal typing takes precedence") {
         it("should use nominal typing when types are nominally compatible") {
            val source = """
               type FirstName inherits String
               type LastName inherits String

               model Person {
                  firstName: FirstName
                  lastName: LastName
               }

               model Employee inherits Person {
                  employeeId: Int
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val employee = schema.model("Employee")

            // Should use nominal inheritance (fast path)
            employee.isAssignableOrErrors(person).shouldBeAssignable()
         }
      }

      describe("mixed scalar and object fields") {
         it("should handle models with both scalar and object fields") {
            val source = """
               type FirstName inherits String
               type LastName inherits String
               type Street inherits String
               type City inherits String
               type Postcode inherits String

               model Address {
                  street: Street
                  city: City
               }

               model Person {
                  firstName: FirstName
                  lastName: LastName
                  address: Address
               }

               model EnrichedPerson {
                  firstName: FirstName
                  lastName: LastName
                  address: {
                     street: Street
                     city: City
                     postcode: Postcode
                  }
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val enrichedPerson = schema.model("EnrichedPerson")

            // Scalars use nominal, objects use structural
            enrichedPerson.isAssignableOrErrors(person).shouldBeAssignable()
         }

         it("should fail if any field is incompatible") {
            val source = """
               type FirstName inherits String
               type DogName inherits String
               type Street inherits String
               type City inherits String

               model Address {
                  street: Street
                  city: City
               }

               model Person {
                  firstName: FirstName
                  address: Address
               }

               model NotCompatible {
                  firstName: DogName
                  address: {
                     street: Street
                     city: City
                  }
               }
            """.trimIndent()

            val schema = source.compiled()
            val person = schema.model("Person")
            val notCompatible = schema.model("NotCompatible")

            // Even though address is compatible, firstName is not
            notCompatible.isAssignableOrErrors(person).shouldNotBeAssignable("NotCompatible is not structurally compatible with Person because Field 'firstName' is has incompatible types of DogName and FirstName")
         }
      }

      describe("edge cases") {
         xit("should handle models with no fields") {
            // This test is invalid, because structural rules only apply where this is structure.
            //. For other scenarios, semantic checks must pass
            val source = """
               model Empty {
               }

               model AlsoEmpty {
               }

               model NotEmpty {
                  field: String
               }
            """.trimIndent()

            val schema = source.compiled()
            val empty = schema.model("Empty")
            val alsoEmpty = schema.model("AlsoEmpty")
            val notEmpty = schema.model("NotEmpty")

            // Empty models are structurally compatible with each other
            alsoEmpty.isAssignableOrErrors(empty).shouldBeAssignable()

            // Non-empty has all fields of empty (none) plus extras
            notEmpty.isAssignableOrErrors(empty).shouldBeAssignable()

            // Empty doesn't have all fields of non-empty
            empty.isAssignableOrErrors(notEmpty).shouldNotBeAssignable("")
         }
      }
   }
})

private fun Either<String, Boolean>.shouldBeAssignable() {
   this.mapLeft { error ->
      fail("Expected types to be assignable, but found errors: $error")
   }
}

private fun Either<String, Boolean>.shouldNotBeAssignable(reason: String) {
   if (this.isRight()) {
      fail("Expected types were not assignable, but they were. Expected reason: $reason")
   }
   this.mapLeft { actualReason ->
      if (reason != actualReason) {
         fail("Types were not assignable (which is correct), but for the wrong reason. Expected: $reason. Actual: $actualReason")
      }
   }
}
