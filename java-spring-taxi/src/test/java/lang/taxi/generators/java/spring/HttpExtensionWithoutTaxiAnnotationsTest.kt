package lang.taxi.generators.java.spring

import lang.taxi.annotations.DataType
import lang.taxi.annotations.Namespace
import lang.taxi.testing.TestHelpers
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

class HttpExtensionWithoutTaxiAnnotationsTest {
   @Namespace("vyne.demo")
   data class CreditCostRequest(val deets: String)

   @Namespace("vyne.demo")
   data class CreditCostResponse(val stuff: String)

   @Namespace("vyne.demo")
   @RestController
   @RequestMapping("/costs")
   class CreditCostService {
      @GetMapping("/interestRates/{clientId}")
      fun getInterestRate(@PathVariable("clientId") @DataType("vyne.demo.ClientId") clientId: String): BigDecimal =
         BigDecimal.ONE

      // Back off, REST snobs.  Method names are here for testing.
      @PostMapping("/{clientId}/doCalculate")
      fun calculateCreditCosts(
         @PathVariable("clientId") @DataType("vyne.demo.ClientId") clientId: String,
         @RequestBody request: CreditCostRequest
      ): CreditCostResponse = CreditCostResponse("TODO")
   }


   @Test
   fun given_getRequestWithPathVariables_then_taxiAnnotationsAreGeneratedCorrectly() {
      val taxiDef = SpringTaxiGenerator.forBaseUrl("http://my-app")
//         .addExtension(SpringMvcExtension.forBaseUrl("http://my-app/"))
         .forClasses(CreditCostService::class.java)
         .generateAsStrings()

      val expected = """
namespace vyne.demo {

    type ClientId inherits String

     model CreditCostRequest {
        deets : String
    }

     model CreditCostResponse {
        stuff : String
    }

    service CreditCostService {
        @HttpOperation(method = "GET" , url = "http://my-app/costs/interestRates/{vyne.demo.ClientId}")
        operation getInterestRate(  clientId: ClientId ) : Decimal
        @HttpOperation(method = "POST" , url = "http://my-app/costs/{vyne.demo.ClientId}/doCalculate")
        operation calculateCreditCosts(  clientId: ClientId, @RequestBody request: CreditCostRequest ) : CreditCostResponse
    }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

}
