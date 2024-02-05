package lang.taxi.packages

import com.google.common.io.Resources
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class PackagePublisherTest {

   // Requires that taxiHub is running
   @Test
   @Disabled
   fun canPublishPackage() {
      val resource = Resources.getResource("testRepo/taxi/lang.taxi.Dummy/0.2.0").toURI()
//      PackagePublisher().publish(Paths.get(resource), releaseType = ReleaseType.MINOR)
   }
}
