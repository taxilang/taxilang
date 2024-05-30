package lang.taxi.writers

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConfigWriterTest {

   @Test
   fun doesNotWriteDefaults() {
      val writer = ConfigWriter()
      val hoconSource = """name: taxi/sample
version: 0.3.0
sourceRoot: src/
additionalSources: {
}
"""
      val result = writer.replaceOrAppendProperty("additionalSources",  mapOf("@orbital/config" to "orbital/config/*.conf"), hoconSource)
      result.shouldBe("""additionalSources {
    "@orbital/config"="orbital/config/*.conf"
}
name="taxi/sample"
sourceRoot="src/"
version="0.3.0"
""")
   }
}
