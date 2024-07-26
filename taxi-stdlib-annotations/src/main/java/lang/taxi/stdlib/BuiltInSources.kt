package lang.taxi.stdlib


import lang.taxi.Compiler
import lang.taxi.TaxiDocument
import lang.taxi.annotations.HttpService
import lang.taxi.functions.stdlib.StdLib
import lang.taxi.sources.SourceCode

/**
 * A list of sources that we implicitly include in the build.
 * This should ultimately be replaced by dependency management.
 *
 * Also, this ideally doesn't belong in taxi-stdlib-annotations, but need
 * somewhere to stick it that's high enough for sharing.
 */
object BuiltInSources {
   val builtInSrc = listOf(
      SourceCode("stdlib.taxi", StdLib.taxi),
      SourceCode("http.taxi", HttpService.asTaxi())
   )
   val builtInTaxi: TaxiDocument = Compiler(builtInSrc).compile()

}
