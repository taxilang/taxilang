package lang.taxi.cli

import com.beust.jcommander.JCommander
import lang.taxi.cli.commands.ProjectShellCommand
import lang.taxi.cli.commands.ProjectlessShellCommand
import lang.taxi.cli.commands.ShellCommand
import lang.taxi.cli.utils.log
import lang.taxi.generators.TaxiEnvironment
import lang.taxi.generators.TaxiProjectEnvironment
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils

@Component
class ShellRunner(
   val commander: JCommander,
   val env: TaxiEnvironment,
   val buildInfo: BuildProperties?,
   val gitProperties: GitProperties?
) : CommandLineRunner {

   override fun run(vararg args: String) {
      val version = buildInfo?.version ?: "Dev version"
      val gitVersion = if (gitProperties != null) "@${gitProperties!!.shortCommitId}" else ""
      log().info("Taxi $version $gitVersion")
      commander.parse(*args)
      val parsedCommand: JCommander
      val commandName = commander.parsedCommand
      try {
         parsedCommand = commander.commands[commandName]!!
      } catch (e: Exception) {
         printUsage()
         return
      }

      // TODO using bootOptions as a static object until cliOptions is arranged.
//        if (env.cliOptions.help) {
      if (TaxiCli.bootOptions.help) {
         if (StringUtils.isEmpty(commandName)) {
            commander.usage()
         } else {
            commander.usage(commandName)
         }
         return
      }

      val shellCommand = parsedCommand.objects[0] as ShellCommand<*>
      when {
         shellCommand is ProjectShellCommand && env !is TaxiProjectEnvironment -> {
            log().error("The ${shellCommand.name} command requires a project to run against.  Try running 'taxi init' to get started")
            return
         }
         shellCommand is ProjectShellCommand && env is TaxiProjectEnvironment -> {
            shellCommand.execute(env as TaxiProjectEnvironment)
         }
         shellCommand is ProjectlessShellCommand -> {
            log().debug("Running ${shellCommand.name} outside of a project")
            shellCommand.execute(env)
         }
         else -> {
            log().error("An internal error occurred - hit an unexpected when branch.  command is of type ${shellCommand::class.simpleName} and env is of type ${env::class.simpleName}")
            return
         }
      }
   }

   private fun printUsage() {
      commander.usage()
   }


}
