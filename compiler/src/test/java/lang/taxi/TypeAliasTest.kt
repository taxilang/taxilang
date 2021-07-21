package lang.taxi

import com.winterbe.expekt.should
import org.junit.jupiter.api.Test

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

   @Test
   fun inlineTypeAliasesWithFullyQualifiedNamesAreNotNamedInTheirNamespace() {
      val src = """
namespace vyne {
    type Client {
        id : vyne.ClientId as String
        name : ClientName as String
        jurisdiction : foo.ClientJurisdiction as String
    }
}
      """.trimIndent()
      val schema = Compiler(src).compile()
      schema.objectType("vyne.Client").field("id").type.qualifiedName.should.equal("vyne.ClientId")
      schema.objectType("vyne.Client").field("jurisdiction").type.qualifiedName.should.equal("foo.ClientJurisdiction")
      schema.objectType("vyne.Client").field("name").type.qualifiedName.should.equal("vyne.ClientName")
   }
}
