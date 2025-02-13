package lang.taxi.codegen.cli

import picocli.CommandLine
import kotlin.system.exitProcess


fun main(args: Array<String>) {
   exitProcess(CommandLine(CodegenCommand()).execute(*args))
}
