package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe

class TaxiQlVariablesSpec : DescribeSpec({
   describe("defining variables in a taxiql query") {
      it("should be possible to define a variable in a given block") {
         val (schema,query) = """model Person {
            | firstName : FirstName inherits String
            |}
         """.compiledWithQuery("given { name : FirstName  } find { Person }")
         val fact = query.facts.single()
         fact.value.hasValue.shouldBeFalse()
         fact.value.variableName.shouldBe("name")
      }
   }
})
