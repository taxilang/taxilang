package lang.taxi.cli.config

import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.TaxiPackageProject
import java.nio.file.Path

class CliTaxiEnvironment(
   override val project: TaxiPackageProject,
   override val projectRoot: Path,
   override val outputPath: Path
) : TaxiEnvironment {
   companion object {
      fun forRoot(root: Path, project: TaxiPackageProject?): TaxiEnvironment {
         return if (project == null) {
            NoProjectEnvironment(root, root)
         } else {
            ProjectEnvironment(project, root, root.resolve(project.output))
         }
      }
   }
}

data class ProjectEnvironment(
   override val project: TaxiPackageProject,
   override val projectRoot: Path,
   override val outputPath: Path
) : TaxiProjectEnvironment

data class NoProjectEnvironment(
   override val projectRoot: Path,
   override val outputPath: Path
) : TaxiEnvironment {
   override val project: TaxiPackageProject? = null
}
