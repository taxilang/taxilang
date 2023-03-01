package lang.taxi.utils

import com.winterbe.expekt.should
import org.junit.jupiter.api.Test

class ListsTest {
   @Test
   fun canPop() {
      val source = listOf("A","B","C")
      val (first,rest) = source.takeHead()
      first.should.equal("A")
      rest.should.equal(listOf("B","C"))
   }

   @Test
   fun `can coalesce empty lists`() {
      (null as List<String>?).coalesceIfEmpty(listOf("a")).should.equal(listOf("a"))
      (null as List<String>?).coalesceIfEmpty( (null as List<String>?)).should.be.`null`
      emptyList<String>().coalesceIfEmpty(listOf("a")).should.equal(listOf("a"))
      listOf("b").coalesceIfEmpty(listOf("a")).should.equal(listOf("b"))
   }
}
