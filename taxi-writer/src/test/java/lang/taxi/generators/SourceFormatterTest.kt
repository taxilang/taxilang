package lang.taxi.generators

import com.winterbe.expekt.expect
import org.junit.jupiter.api.Test

class SourceFormatterTest {

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
