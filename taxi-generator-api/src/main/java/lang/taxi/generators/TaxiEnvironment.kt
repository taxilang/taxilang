package lang.taxi.generators

import lang.taxi.packages.TaxiPackageProject
import java.nio.file.Path

interface TaxiEnvironment {
   val projectRoot: Path
   val outputPath: Path
   val project: TaxiPackageProject
}
