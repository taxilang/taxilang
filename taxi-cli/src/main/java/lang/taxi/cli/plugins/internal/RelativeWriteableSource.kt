package lang.taxi.cli.plugins.internal

import lang.taxi.generators.WritableSource
import java.nio.file.Path

data class RelativeWriteableSource(val relativePath: Path, val source: WritableSource) : WritableSource by source {
   override val path: Path
      get() = relativePath.resolve(source.path)
}
