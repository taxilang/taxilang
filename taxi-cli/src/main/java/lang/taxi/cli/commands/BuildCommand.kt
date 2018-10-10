package lang.taxi.cli.commands

import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.cli.config.TaxiConfig
import lang.taxi.cli.config.TaxiEnvironment
import lang.taxi.cli.plugins.Plugin
import lang.taxi.cli.plugins.PluginRegistry
import lang.taxi.cli.utils.log
import lang.taxi.generators.ModelGenerator
import lang.taxi.generators.WritableSource
import org.antlr.v4.runtime.CharStreams
import org.apache.commons.io.FileUtils
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.file.Path

@Component
class BuildCommand(val config: TaxiConfig, val pluginManager: PluginRegistry) : ShellCommand {
    override val name = "build"

    override fun execute(environment: TaxiEnvironment) {
        val doc = loadSources(environment.sourcePath) ?: return;
        val sourcesToOutput = pluginManager.declaredPlugins
                .filterIsInstance<ModelGenerator>()
                .flatMap {
                    val plugin = it as Plugin
                    log().info("Running generator ${plugin.id}")
                    val generated = it.generate(doc)
                    log().info("Generator ${plugin.id} generated ${generated.size} files")
                    generated
                }

        writeSources(sourcesToOutput, environment.outputPath)
        log().info("Wrote ${sourcesToOutput.size} files to ${environment.outputPath}")
    }

    private fun writeSources(sourcesToOutput: List<WritableSource>, outputPath: Path) {
        sourcesToOutput.forEach { source ->
            val target = outputPath.resolve(source.path)
            FileUtils.forceMkdirParent(target.toFile())
            FileUtils.write(target.toFile(), source.content, Charset.defaultCharset())
        }
    }

    private fun loadSources(path: Path): TaxiDocument? {
        val files = path.toFile().walkTopDown()
                .filter { it.isFile }
                .filter { it.extension == "taxi" }
                .map { file ->
                    CharStreams.fromPath(file.toPath())
                }.toList()
        if (files.isEmpty()) {
            log().warn("No sources were found to compile at $path - exiting")
            return null
        }
        return Compiler(files.toList()).compile()
    }

}