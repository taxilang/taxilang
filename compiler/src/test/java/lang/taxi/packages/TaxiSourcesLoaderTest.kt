package lang.taxi.packages

import com.google.common.io.Resources
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Assertions.*
import kotlin.io.path.toPath

class TaxiSourcesLoaderTest : DescribeSpec({
   describe("taxi sources loader") {
      it("loads correctly if provided path is a directory") {
         val root = Resources.getResource("sample-project").toURI().toPath()
         val taxiProject = TaxiSourcesLoader.loadPackage(root)
         taxiProject.sources.shouldHaveSize(5)
      }

      it("loads correctly if provided path is a taxi.conf file") {
         val root = Resources.getResource("sample-project/taxi.conf").toURI().toPath()
         val taxiProject = TaxiSourcesLoader.loadPackage(root)
         taxiProject.sources.shouldHaveSize(5)
      }

      it("loads with dependencies correctly if provided path is a directory") {
         val root = Resources.getResource("sample-project").toURI().toPath()
         val taxiProject = TaxiSourcesLoader.loadPackageAndDependencies(root)
         taxiProject.sources.shouldHaveSize(5)
      }

      it("loads with dependencies  correctly if provided path is a taxi.conf file") {
         val root = Resources.getResource("sample-project/taxi.conf").toURI().toPath()
         val taxiProject = TaxiSourcesLoader.loadPackageAndDependencies(root)
         taxiProject.sources.shouldHaveSize(5)
      }
   }
 })
