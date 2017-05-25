package lang.taxi

import com.winterbe.expekt.expect
import lang.taxi.types.Field
import lang.taxi.types.ObjectType
import lang.taxi.types.PrimitiveType
import org.junit.Test

class TypeSystemTest {

   @Test
   fun when_typeIsAbsent_then_proxyIsReturned() {
      val typeSystem = typeSystem()
      val type = typeSystem.getOrCreate("lang.taxi.SomeType")
      expect(type).`is`.instanceof(TypeProxy::class.java)
      expect(type.qualifiedName).to.equal("lang.taxi.SomeType")
   }

   @Test
   fun when_typeIsPresent_then_itIsReturned() {
      val typeSystem = typeSystem()
      val realType = ObjectType("lang.taxi.SomeType", emptyList())
      typeSystem.register(realType)
      val type = typeSystem.getOrCreate("lang.taxi.SomeType")
      expect(type).to.equal(realType)
   }

   @Test
   fun given_proxyIsReturned_when_typeIsAdded_then_proxyIsUpdated() {
      val typeSystem = typeSystem()
      val type = typeSystem.getOrCreate("lang.taxi.SomeType")
      expect(type).`is`.instanceof(TypeProxy::class.java)
      val proxy = type as TypeProxy
      expect(proxy.isResolved()).to.be.`false`

      val fields = listOf(Field("name", PrimitiveType.STRING))
      val realType = ObjectType("lang.taxi.SomeType", fields)
      typeSystem.register(realType)
      expect(proxy.isResolved()).to.be.`true`
//      expect(proxy.fields).to.equal(fields)
   }

   @Test
   fun when_allTypesAreResolved_then_containsUnresolvedTypesIsFalse() {
      val typeSystem = typeSystem()
      typeSystem.getOrCreate("lang.taxi.SomeType")
      expect(typeSystem.containsUnresolvedTypes()).to.be.`true`

      val realType = ObjectType("lang.taxi.SomeType", fields = emptyList())
      typeSystem.register(realType)
      expect(typeSystem.containsUnresolvedTypes()).to.be.`false`

   }

   private fun typeSystem(): TypeSystem {
      return TypeSystem()
   }
}

