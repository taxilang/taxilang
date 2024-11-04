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
   }
})
