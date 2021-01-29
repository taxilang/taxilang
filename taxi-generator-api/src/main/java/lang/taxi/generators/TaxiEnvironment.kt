package lang.taxi.generators

import lang.taxi.packages.TaxiPackageProject
import java.nio.file.Path

/**
 * The environment where taxi is running.
 * May or may not contain a project
 */
interface TaxiEnvironment {
   val projectRoot: Path
   val outputPath: Path
   val project: TaxiPackageProject?
}

/**
 * An environment that contains a taxi project
 */
interface TaxiProjectEnvironment : TaxiEnvironment {
   override val project: TaxiPackageProject
}
