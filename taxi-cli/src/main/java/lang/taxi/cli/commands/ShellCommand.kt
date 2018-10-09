package lang.taxi.cli.commands

import lang.taxi.cli.config.TaxiEnvironment

interface ShellCommand {
    val name: String
    fun execute(env: TaxiEnvironment)
}


