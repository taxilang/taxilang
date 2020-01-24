package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.ObjectType
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
   fun when_aTypeIsImportedAndAlsoDeclared_then_qualificationThrowsAmbiguousError() {
      val typeSystem = TypeSystem(listOf(ObjectType.undefined("foo.Customer"), ObjectType.undefined("bar.Customer")))
      Assertions.assertThatThrownBy { typeSystem.qualify("bar", "Customer") }.hasMessageContaining("Name reference Customer is ambiguous, and could refer to any of the available types foo.Customer, bar.Customer")
   }

}
