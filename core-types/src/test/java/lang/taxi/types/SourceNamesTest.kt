package lang.taxi.types

import com.winterbe.expekt.should
import org.apache.commons.lang3.SystemUtils
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class SourceNamesTest {
   @Test
   fun canNormalizePathsOnWindows() {
      assumeTrue(SystemUtils.IS_OS_WINDOWS)
      SourceNames.normalize("""C:\Users\marty\dev\taxi-language-server\taxi-lang-service\target\test-classes\test-scenarios\simple-workspace\financial-terms.taxi""")
         .should.equal("file:///C:/Users/marty/dev/taxi-language-server/taxi-lang-service/target/test-classes/test-scenarios/simple-workspace/financial-terms.taxi")
      SourceNames.normalize("file:///C:/Users/marty/dev/taxi-language-server/taxi-lang-service/target/test-classes/test-scenarios/simple-workspace/trade.taxi")
         .should.equal("file:///C:/Users/marty/dev/taxi-language-server/taxi-lang-service/target/test-classes/test-scenarios/simple-workspace/trade.taxi")
   }

}
