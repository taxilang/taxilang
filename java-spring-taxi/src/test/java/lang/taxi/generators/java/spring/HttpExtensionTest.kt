package lang.taxi.generators.java.spring

import kotlinx.coroutines.flow.Flow
import lang.taxi.annotations.DataType
import lang.taxi.annotations.Namespace
import lang.taxi.annotations.Operation
import lang.taxi.annotations.Service
import lang.taxi.testing.TestHelpers
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal

@DataType
typealias FilmId = Int
class HttpExtensionTest {

   @Namespace("vyne.demo")
   data class CreditCostRequest(val deets: String)

   @Namespace("vyne.demo")
   data class CreditCostResponse(val stuff: String)

   @RestController
   @RequestMapping("/costs")
   @Service("vyne.demo.CreditCostService")
   class CreditCostService {


      @Operation
      @GetMapping("/interestRates/{clientId}")
      fun getInterestRate(@PathVariable("clientId") @DataType("vyne.demo.ClientId") clientId: String): BigDecimal =
         BigDecimal.ONE

      // Back off, REST snobs.  Method names are here for testing.
      @PostMapping("/{clientId}/doCalculate")
      @Operation
      fun calculateCreditCosts(
         @PathVariable("clientId") @DataType("vyne.demo.ClientId") clientId: String,
         @RequestBody request: CreditCostRequest
      ): CreditCostResponse = CreditCostResponse("TODO")

   }

   @Test
   fun `uses type aliases in controller inputs`() {
      data class Film(val id: FilmId)

      @RestController
      class FilmApi {
         @GetMapping("/film/{filmId}")
         fun lookupFilm(@PathVariable("filmId") filmId: FilmId): Film = TODO()
      }

      val taxiDef = SpringTaxiGenerator.forBaseUrl("http://my-app/")
         .forClasses(FilmApi::class.java)
         .generateAsStrings()

      val expected = """
         namespace lang.taxi.generators.java.spring {
            type FilmId inherits Int
            model Film {
               id:FilmId
            }
            service FilmApi {
               @HttpOperation(method = "GET", url="http://my-app/film/{lang.taxi.generators.java.spring.FilmId}")
               operation lookupFilm(filmId : FilmId):Film
            }
         }
      """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_getRequestWithPathVariables_then_taxiAnnotationsAreGeneratedCorrectly() {
      val taxiDef = SpringTaxiGenerator.forBaseUrl("http://my-app/")
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

   @Test
   fun `excluded operations arent generated`() {
      @RestController
      class MyService {
         @GetMapping("/films")
         fun film1(): Film = TODO()

         @GetMapping("/films-flux")
         @Operation(excluded = true)
         fun film2(): Film = TODO()
      }
      val taxiDef = SpringTaxiGenerator.forBaseUrl("http://my-app/")
         .forClasses(MyService::class.java)
         .generateAsStrings()
      val expected = """
namespace lang.taxi.generators.java.spring {
   model Film {
      id : FilmId
   }

   type FilmId inherits Int

   service MyService {
      @HttpOperation(method = "GET" , url = "http://my-app/films")
      operation film1(  ) : Film
   }
}
      """
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @Test
   fun `unwraps response type wrappers`() {

      @RestController
      class MyService {
         @GetMapping("/films")
         fun filmWithResponseEntity(): ResponseEntity<Film> = TODO()

         @GetMapping("/films-flux")
         fun filmWithFlux(): Flux<Film> = TODO()

         @GetMapping("/films-mono")
         fun filmWithMono(): Mono<Film> = TODO()

         @GetMapping("/films-flow")
         fun filmWithFlow(): Flow<Film> = TODO()
      }

      val taxiDef = SpringTaxiGenerator.forBaseUrl("http://my-app/")
         .forClasses(MyService::class.java)
         .generateAsStrings()

      val expected = """
namespace lang.taxi.generators.java.spring {
   model Film {
      id : FilmId
   }

   type FilmId inherits Int

   service MyService {
      @HttpOperation(method = "GET" , url = "http://my-app/films")
      operation filmWithResponseEntity(  ) : Film
      @HttpOperation(method = "GET" , url = "http://my-app/films-flux")
      operation filmWithFlux(  ) : Film[]
      @HttpOperation(method = "GET" , url = "http://my-app/films-mono")
      operation filmWithMono(  ) : Film
      @HttpOperation(method = "GET" , url = "http://my-app/films-flow")
      operation filmWithFlow(  ) : Film[]
   }
}
      """.trimIndent()

      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

}

// We get a GenericSignatureFormatError Signature Parse error
// thrown by the JVM if using a method-level data class in inner-generics
// (ie., if this class is defined in `unwraps response type wrappers`() method.
data class Film(val id: FilmId)
