package lang.taxi

import com.winterbe.expekt.should
import org.junit.Test

// NOte : I'm trying to split tests out from GrammarTest
// to more specific areas.
// There's also a bunch of tests in GrammarTest that cover type aliases
class TypeAliasTest  {
   @Test
   fun canDeclareTypeAliasOnCollection() {
      val src = """
type Person {
   firstName : FirstName as String
}
type alias ListOfPerson as Person[]
      """.trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.typeAlias("ListOfPerson")
      val typeAliasParameterizedName = type.aliasType!!.toQualifiedName().parameterizedName
      typeAliasParameterizedName.should.equal("lang.taxi.Array<Person>")
   }
}
