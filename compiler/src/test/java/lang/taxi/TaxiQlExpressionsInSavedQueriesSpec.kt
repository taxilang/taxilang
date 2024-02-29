package lang.taxi

import io.kotest.core.spec.style.DescribeSpec

class TaxiQlExpressionsInSavedQueriesSpec : DescribeSpec({

   describe("Using expressions within given clauses that reference variables from saved query") {

      val (schema,query) = """
         type HumanId inherits String
         model Person {
            personId : PersonId inherits String
         }
      """.compiledWithQuery("""
         query MyQuery(humanId : HumanId) {
            given { personId : PersonId = (PersonId) humanId }
            find { Person }
         }
      """.trimIndent())
   }
})
