package lang.taxi.cli.config

import lang.taxi.generators.TaxiEnvironment
import lang.taxi.packages.TaxiPackageProject
import java.nio.file.Path

data class CliTaxiEnvironment(
   override val project: TaxiPackageProject,
   override val projectRoot: Path,
   override val outputPath: Path
) : TaxiEnvironment {
   companion object {
      fun forRoot(root: Path, project: TaxiPackageProject): CliTaxiEnvironment {
         return CliTaxiEnvironment(project,
            root,
            root.resolve(project.output))
      }
   }
}
