package lang.taxi.generators.java.extensions

import com.winterbe.expekt.expect
import lang.taxi.annotations.DataType
import lang.taxi.annotations.Namespace
import lang.taxi.annotations.Operation
import lang.taxi.annotations.Service
import lang.taxi.generators.java.DefaultServiceMapper
import lang.taxi.generators.java.TaxiGenerator
import lang.taxi.types.Annotation
import org.junit.Test
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

class HttpExtensionTest {

   data class CreditCostRequest(val deets:String)
   data class CreditCostResponse(val stuff:String)
   @RestController
   @RequestMapping("/costs")
   @Service("CreditCostService")
   @Namespace("polymer.creditInc.creditMarkup")
   class CreditCostService {

      @GetMapping("/interestRates")
      fun getInterestRate(@PathVariable("clientId") clientId:String):BigDecimal = BigDecimal.ONE

      // Back off, REST snobs.  Method names are here for testing.
      @PostMapping("/{clientId}/doCalculate")
      @Operation
      fun calculateCreditCosts( @PathVariable("clientId") @DataType("ClientId") clientId: String,@RequestBody request: CreditCostRequest): CreditCostResponse = CreditCostResponse("TODO")

   }


   @Test
   fun given_getRequestWithPathVariables_then_taxiAnnotationsAreGeneratedCorrectly() {
      class MockAddressProvider : HttpServiceAddressProvider {
         override fun httpAddress(): Annotation = Annotation("ServiceDiscoveryClient", mapOf("serviceName" to "mockService"))

      }
      val taxiDef = TaxiGenerator(serviceMapper = DefaultServiceMapper(
         operationExtensions = listOf(SpringMvcHttpOperationExtension()),
         serviceExtensions = listOf(SpringMvcHttpServiceExtension(MockAddressProvider()))
      )).forClasses(CreditCostRequest::class.java, CreditCostResponse::class.java, CreditCostService::class.java).generateAsStrings()

      expect(taxiDef.first()).to.equal("")
   }

   @Test
   fun given_requestBodyPresentOnParam_then_taxiAnnotationIsPresentInSchema() {

   }

}
