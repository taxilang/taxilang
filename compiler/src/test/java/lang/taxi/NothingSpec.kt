package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import lang.taxi.types.PrimitiveType

class NothingSpec :  DescribeSpec({
   describe("nothing type") {
      it("is possible to assign nothing to everything") {
         val schema = """
            model Person {
               id : PersonId inherits String
            }
         """.compiled()
         val nothing = schema.type("lang.taxi.Nothing")
         nothing.isAssignableTo(schema.type("Person")).shouldBeTrue()
         nothing.isAssignableTo(schema.type("lang.taxi.Array<Person>")).shouldBeTrue()
         nothing.isAssignableTo(PrimitiveType.VOID).shouldBeTrue()
      }
      it("can use function that returns nothing in when clause ") {
         val schema = """
            model Person {
                id: PersonId inherits Int
            }

            model BadRequestError

            extension function requireNotEmpty(collection: Person[]):Person[] -> when {
                collection.size() == 0 -> throw(BadRequestError)
                else -> collection
            }
         """.compiled()

      }
   }
}) {
}
