package lang.taxi.linter.rules

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import lang.taxi.linter.LinterRules
import lang.taxi.validated

class OperationWithInputShouldDeclareContractSpec : DescribeSpec({
   describe("Operation with input should declare a contract liner rule") {
      it("should detect operations with inputs that don't declare a contract") {
         val messages = """
model Person {
   name : Name inherits String
   id : PersonId inherits String
}
service People {
   operation getPerson(PersonId):Person
}
         """.validated(
            linterRules = LinterRules.onlyEnable(OperationWithInputShouldDeclareContract)
         )

         messages.shouldHaveSize(1)
      }

      it("should not add warnings on table operations") {
         val messages = """
model Person {
   name : Name inherits String
   id : PersonId inherits String
}
service People {
   table person : Person[]
}
         """.validated(
            linterRules = LinterRules.onlyEnable(OperationWithInputShouldDeclareContract)
         )

         messages.shouldBeEmpty()
      }

      it("should not add warnings on mutations operations") {
         val messages = """
model Person {
   name : Name inherits String
   id : PersonId inherits String
}
service People {
      write operation updatePerson(PersonId):Person
}
         """.validated(
            linterRules = LinterRules.onlyEnable(OperationWithInputShouldDeclareContract)
         )

         messages.shouldBeEmpty()
      }
   }
})
