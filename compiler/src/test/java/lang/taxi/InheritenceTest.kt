package lang.taxi

import org.junit.Ignore
import org.junit.Test

// NOte : I'm trying to split tests out from GrammarTest
// to more specific areas.
// There's also a bunch of tests in GrammarTest that cover type Inheritence
class InheritenceTest {

   @Test
   @Ignore("TODO")
   fun canInheritFromCollection() {
      val src = """
type Person {
   firstName : FirstName as String
}
type ListOfPerson inherits Person[]
      """.trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.type("ListOfPerson")
      TODO()
   }
}
