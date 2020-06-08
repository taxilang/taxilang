package lang.taxi.cli.commands

import lang.taxi.cli.config.CliTaxiEnvironment
import lang.taxi.generators.TaxiEnvironment

interface ShellCommand {
    val name: String
    fun execute(environment: TaxiEnvironment)
}


