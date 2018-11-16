package lang.taxi.generators

import lang.taxi.TaxiDocument
import java.nio.file.Path

interface ModelGenerator {
//    val processors: List<Processor>

    fun generate(taxi: TaxiDocument, processors: List<Processor>): List<WritableSource>
}

interface WritableSource {
    val path: Path
    val content: String
}
