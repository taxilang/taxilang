package lang.taxi.cli.plugins.internal

import lang.taxi.cli.commands.BuildCommand
import lang.taxi.cli.config.CliTaxiEnvironment
import lang.taxi.cli.pluginArtifacts
import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.generators.TaxiProjectEnvironment
import lang.taxi.packages.TaxiProjectLoader
import java.nio.file.Path

fun executeBuild(path: Path, plugins: List<InternalPlugin>) {
   val project = TaxiProjectLoader(path.resolve("taxi.conf")).load()
   val build = BuildCommand(
      PluginRegistry(
         internalPlugins = plugins,
         requiredPlugins = project.pluginArtifacts()
      )
   )
   val environment = CliTaxiEnvironment.forRoot(path, project) as TaxiProjectEnvironment
   build.execute(environment)
}
