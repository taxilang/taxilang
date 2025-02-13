package lang.taxi.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PathGlobTest {

   @Test
   fun normalizeGlobPattern() {
      PathGlob.expandGlobWithBaseDirectory("**/*.taxi").shouldBe("{*.taxi,**/*.taxi}")
      PathGlob.expandGlobWithBaseDirectory("src/**/*.taxi").shouldBe("{src/*.taxi,src/**/*.taxi}")
   }
}
