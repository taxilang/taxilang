package lang.taxi.lsp

import lang.taxi.packages.MessageLogger
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.sources.SourceCode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Responsible in a LSP service for deciding where to discover sources from.
 * If we find we're running in a taxi project (ie., taxi.conf in the root), then
 * use that to determine where to load sources from.
 *
 * If there's a taxi project with dependencies, will trigger the package loader to go and
 * fetch them.
 *
 * Otherwise, just takes all the sources sitting under the root that end in .taxi
 */
class WorkspaceSourceService(
    private val root: Path,
    private val messageLogger: MessageLogger
) {

    fun loadSources(): Sequence<SourceCode> {
        val taxiConfFile = root.resolve("taxi.conf")
        return if (Files.exists(taxiConfFile)) {
            val packageSources = TaxiSourcesLoader.loadPackageAndDependencies(root, messageLogger)
            packageSources.sources.asSequence()
        } else {
            loadAllTaxiFilesUnderRoot()
        }
    }

    private fun loadAllTaxiFilesUnderRoot(): Sequence<SourceCode> {
        return root.toFile()
            .walk()
            .filter { it.extension == "taxi" && !it.isDirectory }
            .map { file -> SourceCode.from(file) }
//            .forEach { file ->
//                val source = file.readText()
//                 Note - use the uri from the path, not the file, to ensure consistency.
//                 on windows, file uri's are file:///C:/ ... and path uris are file:///c:/...
//                compilerService.updateSource(SourceNames.normalize(file.toPath().toUri().toString()), source)
//            }
    }
}