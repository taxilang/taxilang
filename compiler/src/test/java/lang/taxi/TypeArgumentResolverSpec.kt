package lang.taxi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TypeArgumentResolverSpec : DescribeSpec({
   describe("type argument resolver") {
       it("should resolve the type arguments of a varargs function") {
           """
               declare function <T> hello (T...):String

               model Person {
                  first: FirstName inherits String
                  last : LastName inherits String
                  greeting : hello(FirstName, LastName)
               }
           """.compiled()
               .objectType("Person")
               .field("greeting")
               .type.qualifiedName.shouldBe("lang.taxi.String")
       }

       // This doesn't work - as there's no way to resolve <T>.
       // Need to think of how to support this
       xit("should resolve the type arguments of a varargs function with empty args") {
           """
               declare function <T> hello (T...):String

               model Person {
                  first: FirstName inherits String
                  last : LastName inherits String
                  greeting : String = hello()
               }
           """.compiled()
               .objectType("Person")
               .field("greeting")
               .type.qualifiedName.shouldBe("lang.taxi.String")
       }
   }
})
