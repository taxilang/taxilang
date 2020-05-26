package lang.taxi.types

import com.winterbe.expekt.expect
import org.junit.Assert.*
import org.junit.Test

class QualifiedNameParserTest {

   @Test
   fun parsesNameWithUnderscoresCorrectly() {
      val fqn = "foo.baz.Bar_Bop".fqn()
      expect(fqn.typeName).to.equal("Bar_Bop")
      expect(fqn.fullyQualifiedName).to.equal("foo.baz.Bar_Bop")
      expect(fqn.parameters).to.be.empty
   }

   @Test
   fun parsesUnderscoresInGenericsCorrectly() {
      val genericFqn = "foo.baz.Bar_Bop<foo.baz.Sup_Dog>".fqn()
      expect(genericFqn.typeName).to.equal("Bar_Bop")
      expect(genericFqn.fullyQualifiedName).to.equal("foo.baz.Bar_Bop")
      expect(genericFqn.parameters).to.have.size(1)
      expect(genericFqn.parameters[0].typeName).to.equal("Sup_Dog")
      expect(genericFqn.parameters[0].fullyQualifiedName).to.equal("foo.baz.Sup_Dog")
      expect(genericFqn.parameters[0].parameters).to.be.empty
   }

   @Test
   fun parsesNamesCorrectly() {
      val fqn = "foo.baz.Bar".fqn()
      expect(fqn.typeName).to.equal("Bar")
      expect(fqn.fullyQualifiedName).to.equal("foo.baz.Bar")
      expect(fqn.parameters).to.be.empty
   }

   @Test
   fun parsesParamterizedNamesCorrectly() {
      val fqn = "foo.baz.Bar<some.Thing<a.B,c.D>,some.other.Thing<d.E,f.G>>".fqn()
      expect(fqn.parameters).to.have.size(2)

      expect(fqn.parameters[0].fullyQualifiedName).to.equal("some.Thing")
      expect(fqn.parameters[0].parameters).to.have.size(2)
      expect(fqn.parameters[0].parameters[0].fullyQualifiedName).to.equal("a.B")
      expect(fqn.parameters[0].parameters[1].fullyQualifiedName).to.equal("c.D")

      expect(fqn.parameters[1].fullyQualifiedName).to.equal("some.other.Thing")
      expect(fqn.parameters[1].parameters).to.have.size(2)
      expect(fqn.parameters[1].parameters[0].fullyQualifiedName).to.equal("d.E")
      expect(fqn.parameters[1].parameters[1].fullyQualifiedName).to.equal("f.G")
   }

   @Test
   fun parsesArrayShorthandCorrectly() {
      val fqn = "sample.Foo[]".fqn()
      expect(fqn.parameters).to.have.size(1)
      expect(fqn.parameters[0].fullyQualifiedName).to.equal("sample.Foo")
      expect(fqn.typeName).to.equal("Array")
   }
}

private fun String.fqn(): QualifiedName {
   return QualifiedNameParser.parse(this)
}


