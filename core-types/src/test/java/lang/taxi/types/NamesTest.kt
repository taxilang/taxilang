package lang.taxi.types

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NamesTest {
   @Test
   fun `When namespace matches other namespace typename is qualified relatively`() {
      val typeName = "Price"
      val names = QualifiedName("ns", typeName)
      val qualifiedRelativeTo = names.qualifiedRelativeTo("ns")
      assertEquals(typeName, qualifiedRelativeTo)
   }

   @Test
   fun `When taxi native namespace contains namespace typename is qualified relatively`() {
      val typeName = "Price"
      val names = QualifiedName("lang.taxi", typeName)
      val qualifiedRelativeTo = names.qualifiedRelativeTo("ns")
      assertEquals(typeName, qualifiedRelativeTo)
   }

   @Test
   fun `When namespace is empty typename is qualified relatively`() {
      val typeName = "Price"
      val names = QualifiedName("", typeName)
      val qualifiedRelativeTo = names.qualifiedRelativeTo("ns")
      assertEquals(typeName, qualifiedRelativeTo)
   }

   @Test
   fun `When namespace is not empty and not related to native namespaces and not the same as other namespace FQ typename is qualified relatively`() {
      val typeName = "Price"
      val names = QualifiedName("hft", typeName)
      val qualifiedRelativeTo = names.qualifiedRelativeTo("ns")
      assertEquals("hft.$typeName", qualifiedRelativeTo)
   }
}
