package lang.taxi.cli.commands

import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.TaxiProjectEnvironment

interface ShellCommand<T : TaxiEnvironment> {
    val name: String
    fun execute(environment: T)
}
interface ProjectlessShellCommand : ShellCommand<TaxiEnvironment>
interface ProjectShellCommand : ShellCommand<TaxiProjectEnvironment>


