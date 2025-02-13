package lang.taxi.codegen

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import lang.taxi.CompilationError
import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.errors
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.query.TaxiQlQuery
import lang.taxi.utils.PathGlob
import lang.taxi.utils.log
import lang.taxi.warnings
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CodePointCharStream
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.time.measureTimedValue

abstract class BaseCodeGenerator {

   fun loadTaxi(configLocation: String): Pair<List<CompilationError>,TaxiDocument>{
      return if (configLocation.startsWith("http")) {
         loadRemoteTaxi(configLocation)
      } else {
         loadTaxiProject(configLocation)
      }
   }

   private fun loadTaxiProject(configLocation: String): Pair<List<CompilationError>,TaxiDocument> {
      val timed = measureTimedValue {
         val configPath = Paths.get(configLocation)
         if (!Files.exists(configPath)) {
            throw FileNotFoundException("Taxi project not found at $configLocation")
         }
         log().info("Loading taxi project from ${configPath.absolutePathString()}")
         val project = TaxiSourcesLoader.loadPackageAndDependencies(configPath.toAbsolutePath())
         Compiler(project).compileWithMessages()
      }
      val (messages, doc) = timed.value
      log().info("Taxi compilation completed in ${timed.duration} with ${messages.errors().size} errors and ${messages.warnings().size} warnings")
      return messages to doc
   }


   private fun loadRemoteTaxi(configLocation: String): Pair<List<CompilationError>,TaxiDocument> {
      TODO("Not yet implemented")
   }


   protected abstract fun generateCodeForQueries(
      queries: Set<TaxiQlQuery>,
      src: TaxiDocument,
      inlineSourceFiles: List<CodePointCharStream>
   ): List<GeneratedCode>

   fun generate(
      querySourcesGlob: PathGlob,
      schema: TaxiDocument,
      inlineSources: List<String> = emptyList(),
   ): Either<List<CompilationError>, List<GeneratedCode>> {
      val querySourceFiles = querySourcesGlob.mapEachDirectoryEntry { entry -> CharStreams.fromPath(entry) }
         .values.toList()

      val inlineSourceFiles = inlineSources.mapIndexed { index,  inlineSource ->
         val filename = InlineFiles.getFilenameFromComment(inlineSource, default = "UnknownSource$index")
         CharStreams.fromString(inlineSource, filename)
      }
      val (messages, src) = Compiler(inputs = querySourceFiles + inlineSourceFiles, importSources = listOf(schema))
         .compileWithMessages()
      val result = if (messages.errors().isNotEmpty()) {
         messages.errors().left()
      } else {
         generateCodeForQueries(src.queries, src, inlineSourceFiles).right()
      }
      return result
   }

   fun generateAndWrite(
      sourceGlob: PathGlob,
      taxiSchema: TaxiDocument,
      outputPath: Path,
      inlineSources: List<String>
   ): Either<List<CompilationError>, List<Path>> {
      val codeGenResult = generate(sourceGlob, taxiSchema, inlineSources)
      return codeGenResult
         .map { generatedCodeFiles ->
            generatedCodeFiles.map { generatedCode ->
               val path = outputPath.resolve(generatedCode.path)
               path.parent.toFile().mkdirs()
               path.writeText(generatedCode.content)
               log().info("Generated code written to $path")
               path
            }
         }
   }
}

data class GeneratedCode(val path: Path, val content: String)
