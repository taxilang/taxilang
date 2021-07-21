package lang.taxi.cli.init

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ProjectInitTest {
   @TempDir
   @JvmField
   var folder: Path? = null

   @Test
   fun whenRunFromAnEmptyDirectory_then_helpfulMessagesAreShown() {

   }
}
