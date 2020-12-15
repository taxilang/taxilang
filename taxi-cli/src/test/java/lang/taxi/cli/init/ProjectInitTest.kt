package lang.taxi.cli.init

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectInitTest {
   @Rule
   @JvmField
   val folder = TemporaryFolder()

   @Test
   fun whenRunFromAnEmptyDirectory_then_helpfulMessagesAreShown() {

   }
}
