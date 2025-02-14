package lang.taxi.writers

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HoconPrettifierTest {
    @Test
    fun `formats hocon`() {
       val src = """name: com.foo/asdf
version: 0.1.0
sourceRoot: src/
additionalSources: {"@orbital/config"="orbital/config/*.conf", "@orbital/nebula"="orbital/nebula/*.nebula.kts"}
dependencies: {"com.orbitalhq/core"="github:orbitalapi/orbital-core-taxi#0.34.0"}"""
       val formatted = HoconPrettifier.format(src)
       formatted.shouldBe("""name: com.foo/asdf
version: 0.1.0
sourceRoot: src/
additionalSources: {
  "@orbital/config" : "orbital/config/*.conf",
  "@orbital/nebula" : "orbital/nebula/*.nebula.kts"
}
dependencies: {
  "com.orbitalhq/core" : "github:orbitalapi/orbital-core-taxi#0.34.0"
}""")
    }
 }
