package lang.taxi

import com.winterbe.expekt.should
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import lang.taxi.messages.Severity
import org.junit.jupiter.api.Test

// NOte : I'm trying to split tests out from GrammarTest
// to more specific areas.
// There's also a bunch of tests in GrammarTest that cover type aliases
class TypeAliasTest  {
   @Test
   fun canDeclareTypeAliasOnCollection() {
      val src = """
type Person {
   firstName : FirstName inherits String
}
type alias ListOfPerson as Person[]
      """.trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.typeAlias("ListOfPerson")
      val typeAliasParameterizedName = type.aliasType!!.toQualifiedName().parameterizedName
      typeAliasParameterizedName.should.equal("lang.taxi.Array<Person>")
   }

   /**
    * This is a conscious choice, after seeing how confusing
    * this is, and the impact of getting it wrong.
    *
    * aliasing T = String is saying "All T's are Strings, and
    * all Strings are T's", which is dangerous and wrong.
    *
    * People almost always mean T inherits String
    *
    */
   @Test
   fun `it is an error to declare an alias of a primitive`() {
      val errors = """
         type alias PersonName as String
      """.validated()
      errors.shouldHaveSize(1)
      errors.single().severity.shouldBe(Severity.ERROR)
   }

   /**
    * This is a conscious choice, after seeing how confusing
    * this is, and the impact of getting it wrong.
    *
    * aliasing T = String is saying "All T's are Strings, and
    * all Strings are T's", which is dangerous and wrong.
    *
    * People almost always mean T inherits String
    *
    */
   @Test
   fun `it is an error to declare an inline alias of a primitive`() {
      val errors = """
         model Person {
            name : PersonName as String
         }
      """.validated()
      errors.shouldHaveSize(1)
      errors.single().severity.shouldBe(Severity.ERROR)
   }
}
