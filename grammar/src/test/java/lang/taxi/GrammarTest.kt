package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.types.ArrayType
import lang.taxi.types.PrimitiveType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File

class GrammarTest {

   @Rule @JvmField
   val rule = ExpectedException.none()

   @Test
   fun canParseSimpleDocument() {
      val doc = Compiler(testResource("simpleType.taxi")).compile()
      expect(doc.namespace).to.equal("lang.taxi")
      val personType = doc.objectType("Person")
      expect(personType.field("firstName").type).to.equal(PrimitiveType.STRING)
      expect(personType.field("firstName").nullable).to.be.`false`
      expect(personType.field("title").nullable).to.be.`true`
      expect(personType.field("friends").type).to.be.instanceof(ArrayType::class.java)
      val friendArray = personType.field("friends").type as ArrayType
      expect(friendArray.type).to.equal(personType)
   }

   @Test
   fun canReferenceTypeBeforeItIsDeclared() {
      val source = """
type Person {
   email : Email
}
type Email {
   value : String
}
"""
      val doc = Compiler(source).compile()
      val email = doc.objectType("Person").field("email")
      expect(email.type).to.equal(doc.objectType("Email"))
   }

   @Test
   fun throwsExceptionOnUnresolvedType() {
      rule.expect(CompilationException::class.java)
      rule.expectMessage(ErrorMessages.unresolvedType("Bar"))
      val source = """
type Foo {
   bar : Bar
}
"""
      Compiler(source).compile()

   }

   private fun testResource(s: String): File {
      return File(this::class.java.classLoader.getResource(s).toURI())
   }
}
