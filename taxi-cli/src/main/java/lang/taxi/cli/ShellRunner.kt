package lang.taxi.cli

import com.beust.jcommander.JCommander
import lang.taxi.cli.commands.ShellCommand
import lang.taxi.cli.config.TaxiConfig
import lang.taxi.cli.config.TaxiEnvironment
import lang.taxi.cli.utils.log
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils

@Component
class ShellRunner(
        val commander: JCommander,
        val env:TaxiEnvironment
) : CommandLineRunner {

    override fun run(vararg args: String) {
        commander.parse(*args)
        val parsedCommand: JCommander
        val commandName = commander.parsedCommand
        try {
            parsedCommand = commander.commands[commandName]!!
        } catch (e: Exception) {
            printUsage()
            return
        }

        if (env.cliOptions.help) {
            if (StringUtils.isEmpty(commandName)) {
                commander.usage()
            } else {
                commander.usage(commandName)
            }
            return
        }

        val shellCommand = parsedCommand.objects[0] as ShellCommand
        log().debug("Running {}", shellCommand.name)

        shellCommand.execute(env)
    }

    private fun printUsage() {
        commander.usage()
    }


}
