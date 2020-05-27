package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.ObjectType
import lang.taxi.types.QualifiedName
import org.assertj.core.api.Assertions
import org.junit.Assert.*
import org.junit.Test

class TypeSystemTest {

   @Test
   fun typeNamesShouldBeCorrectlyQualified() {
      val typeSystem = TypeSystem(emptyList())
      typeSystem.qualify("foo", "Customer").should.equal("foo.Customer")
      typeSystem.qualify("foo", "bar.Customer").should.equal("bar.Customer")
      typeSystem.qualify("", "Customer").should.equal("Customer")
   }

   @Test
   fun importedTypesShouldBeResolved() {
      val typeSystem = TypeSystem(listOf(ObjectType.undefined("foo.Customer")))
      typeSystem.qualify("bar", "Customer").should.equal("foo.Customer")
      typeSystem.qualify("bar", "baz.Customer").should.equal("baz.Customer")
   }

   @Test
   fun when_aTypeResolvesToMultipleTypes_then_qualificationThrowsAmbiguousError() {
      val typeSystem = TypeSystem(listOf(ObjectType.undefined("foo.Customer"), ObjectType.undefined("bar.Customer")))
      Assertions.assertThatThrownBy { typeSystem.qualify("xxx", "Customer") }.hasMessageContaining("Name reference Customer is ambiguous, and could refer to any of the available types foo.Customer, bar.Customer")
   }

   @Test
   fun `when qualifying types if there is a match in the current namespace that hasn't been imported, then it is resolved`() {
      val typeSystem = TypeSystem(listOf(ObjectType.undefined("foo.Customer"), ObjectType.undefined("bar.Customer")))
      typeSystem.qualify("foo","Customer").should.equal("foo.Customer")
   }
   @Test
   fun `when qualifying types if there is an explict import AND a match in the current namespace, then the import wins`() {
      val typeSystem = TypeSystem(listOf(ObjectType.undefined("foo.Customer"), ObjectType.undefined("bar.Customer")))
      typeSystem.qualify("foo","Customer", listOf(QualifiedName.from("bar.Customer"))).should.equal("bar.Customer")
   }

}
