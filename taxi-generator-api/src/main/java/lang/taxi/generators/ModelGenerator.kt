package lang.taxi.generators

import lang.taxi.TaxiDocument
import java.nio.file.Path

interface ModelGenerator {
//    val processors: List<Processor>

   fun generate(taxi: TaxiDocument, processors: List<Processor>, environment: TaxiProjectEnvironment): List<WritableSource>
}

interface WritableSource {
   val path: Path
   val content: String
}

data class SimpleWriteableSource(override val path: Path, override val content: String):WritableSource
