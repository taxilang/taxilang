package lang.taxi.generators

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import org.junit.jupiter.api.Test
import kotlin.math.exp

class SourceFormatterTest {

   @Test
   fun `json inside comments do not affect formatter`() {
      val source = """
         namespace foo {

         [[[
         This is generated. The original source is below:

         ```
         { json : Foo { Bar { Baz }
         }
         }
         ```
         ]]]
            model MyModel {
               foo: String
            }

         }
      """.trimIndent()
      val result = SourceFormatter().format(source)
      val expected = """namespace foo {

   [[[
   This is generated. The original source is below:

   ```
   { json : Foo { Bar { Baz }
   }
   }
   ```
   ]]]
   model MyModel {
      foo: String
   }

}"""
   }

   @Test
   fun shouldIndentCorrectly() {
      val source = """
@ServiceDiscoveryClient(serviceName = "customer-service")
    service CustomerService {
        @HttpOperation(method = "GET" , url = "/customers/email/{demo.CustomerEmailAddress}")
        operation getCustomerByEmail(  demo.CustomerEmailAddress ) : demo.Customer
    }
""".trim()
      val result = SourceFormatter().format(source)
        val expected = """
@ServiceDiscoveryClient(serviceName = "customer-service")
service CustomerService {
   @HttpOperation(method = "GET" , url = "/customers/email/{demo.CustomerEmailAddress}")
   operation getCustomerByEmail(  demo.CustomerEmailAddress ) : demo.Customer
}
        """.trim()

        expect(result).to.equal(expected)
    }

    @Test
    fun canInlineTypeAliases() {
        val source = """
type Customer {
        email : CustomerEmailAddress
        id : CustomerId
        name : String
    }
type alias CustomerEmailAddress as String
@ServiceDiscoveryClient(serviceName = "customer-service")
    service CustomerService {
        @HttpOperation(method = "GET" , url = "/customers/email/{demo.CustomerEmailAddress}")
        operation getCustomerByEmail(  demo.CustomerEmailAddress ) : demo.Customer
    }
type alias CustomerId as Int
        """.trim()

        val formatted = SourceFormatter(inlineTypeAliases = true ).format(source)

        val expected = """
type Customer {
   email : CustomerEmailAddress as String
   id : CustomerId as Int
   name : String
}
@ServiceDiscoveryClient(serviceName = "customer-service")
service CustomerService {
   @HttpOperation(method = "GET" , url = "/customers/email/{demo.CustomerEmailAddress}")
   operation getCustomerByEmail(  demo.CustomerEmailAddress ) : demo.Customer
}
        """.trim()

        expect(formatted).to.equal(expected)

    }
}
