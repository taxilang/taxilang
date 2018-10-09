package lang.taxi.cli.config

import lang.taxi.cli.CliOptions
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.nio.file.Paths

@Component
data class TaxiEnvironment(
        val config: TaxiConfig,
        val cliOptions: CliOptions
) {
    val sourcePath: Path = Paths.get(cliOptions.projectHome, config.project.sourceFolder)

    val outputPath:Path = Paths.get(cliOptions.projectHome, config.project.outputFolder)
}