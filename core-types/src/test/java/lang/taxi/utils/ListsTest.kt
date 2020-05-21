package lang.taxi.utils

import com.winterbe.expekt.should
import org.junit.Test

class ListsTest {
   @Test
   fun canPop() {
      val source = listOf("A","B","C")
      val (first,rest) = source.takeHead()
      first.should.equal("A")
      rest.should.equal(listOf("B","C"))
   }
}
