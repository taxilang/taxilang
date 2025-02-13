package lang.taxi.codegen.cli

import arrow.core.Either
import lang.taxi.CompilationError
import lang.taxi.TaxiDocument
import lang.taxi.codegen.BaseCodeGenerator
import lang.taxi.codegen.ts.react.ReactCodegen
import lang.taxi.errors
import lang.taxi.utils.glob
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.util.concurrent.Callable

@Command(
   name = "taxi-codegen",
   description = ["Generates type-safe code from Taxi query files"],
   mixinStandardHelpOptions = true,
   version = ["1.0"]
)
class CodegenCommand : Callable<Int> {
   @Spec
   private lateinit var spec: Model.CommandSpec // gives access to stderr/stdout


   @Option(
      names = ["-c", "--config"],
      description = ["Path to taxi.conf file, or URL to fetch Taxi schema"]
   )
   private var configLocation: String = "taxi.conf"

   @Option(
      names = ["-p", "--path"],
      description = ["Glob pattern for .taxi source files"],
      defaultValue = "**/*.taxi"
   )
   private var sourcePattern: String = "**/*.taxi"

   @Option(
      names = ["-o", "--output"],
      description = ["Output directory for generated code"],
      defaultValue = "src/generated"
   )
   private var outputDir: File = File("src/generated")

   @Option(
      names = ["-s", "--source"],
      description = ["A string of taxi source"],
   )
   private var sources: Array<String> = arrayOf()

   private val workingDirectory: File = File(System.getProperty("user.dir"))

   override fun call(): Int {
      try {

         val codeGenerator = getCodeGenerator()
         val (errors, taxiSchema) = codeGenerator.loadTaxi(configLocation)
         exitOnErrors(errors, taxiSchema)

         val sourceGlob = try {
            // Attempt to treat sourcePattern as a file path
            val sourcePatternPath = Paths.get(sourcePattern)

            if (sourcePatternPath.isAbsolute) {
               throw UnsupportedOperationException("Absolute paths are not supported yet")
            } else {
               // If it's a relative file path, resolve it against the working directory
               workingDirectory.glob(sourcePatternPath.toString())
            }
         } catch (e: InvalidPathException) {
            // If sourcePattern is not a valid file path (likely a glob pattern), handle it here
            workingDirectory.glob(sourcePattern)
         }


         val errorsOrUpdates = codeGenerator.generateAndWrite(
            sourceGlob,
            taxiSchema,
            outputDir.absoluteFile.toPath(),
            sources.toList()
         )
         when (errorsOrUpdates) {
            is Either.Right -> {
               val updatedPaths = errorsOrUpdates.value
               if (updatedPaths.isEmpty()) {
                  printOut("Warning: No code was generated working with glob pattern of $sourcePattern against directory ${workingDirectory.absolutePath}")
               } else {
                  printOut("Wrote ${updatedPaths.size} files")
               }
            }

            is Either.Left -> {
               val errors = errorsOrUpdates.value
               errors.forEach { error ->
                  val errorPath =
                     error.sourceName?.let {
                        val errorPath = Paths.get(it).toAbsolutePath().normalize()  // Convert to absolute path
                        workingDirectory.toPath().toAbsolutePath().normalize().relativize(errorPath).toString()
                     } ?: "Unknown source"
                  printErr("[$errorPath] - ${error.detailMessage}")
               }
               throw CliException("There are compilation errors", ExitCode.USAGE)
            }
         }
      } catch (e: CliException) {
         printErr(e.message!!)
         return e.exitCode.code
      }

      return 0
   }

   private fun getCodeGenerator(): BaseCodeGenerator {
      // TODO: Others...
      return ReactCodegen()
   }



   private fun exitOnErrors(messages: List<CompilationError>, doc: TaxiDocument): TaxiDocument {
      if (messages.errors().isNotEmpty()) {
         val errorMessage = messages.errors().joinToString("\n")
         throw CliException(errorMessage, ExitCode.USAGE)
      } else {
         return doc
      }
   }

   private fun printOut(message: String) = spec.commandLine().out.println(message)
   private fun printErr(message: String) = spec.commandLine().err.println(message)
}

class CliException(msg: String, val exitCode: ExitCode) : RuntimeException(msg)


enum class ExitCode(val code: Int) {
   OK(CommandLine.ExitCode.OK),
   USAGE(CommandLine.ExitCode.USAGE),
   SOFTWARE(CommandLine.ExitCode.SOFTWARE)
}
