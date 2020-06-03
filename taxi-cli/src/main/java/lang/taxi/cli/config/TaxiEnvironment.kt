package lang.taxi.cli.config

import lang.taxi.cli.CliOptions
import lang.taxi.cli.TaxiCli
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths

@Component
data class TaxiEnvironment(
        val config: TaxiConfig//,

   // TODO using bootOptions as a static object until cliOptions is arranged.
//        val cliOptions: CliOptions
) {
   // TODO using bootOptions as a static object until cliOptions is arranged.
//    val sourcePath: Path = Paths.get(cliOptions.projectHome, config.project.sourceFolder)
    val sourcePath: Path = Paths.get(TaxiCli.bootOptions.projectHome, config.project.sourceFolder)

   // TODO using bootOptions as a static object until cliOptions is arranged.
//    val outputPath:Path = Paths.get(cliOptions.projectHome, config.project.outputFolder)
    val outputPath:Path = Paths.get(TaxiCli.bootOptions.projectHome, config.project.outputFolder)
}
