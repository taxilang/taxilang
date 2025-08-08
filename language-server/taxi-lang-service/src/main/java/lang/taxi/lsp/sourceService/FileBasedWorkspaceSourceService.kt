package lang.taxi.lsp.sourceService

import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.TaxiPackageSources
import lang.taxi.packages.TaxiSourcesLoader
import lang.taxi.sources.SourceCode
import lang.taxi.types.SourceNames
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.LanguageClient
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isDirectory

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
open class FileBasedWorkspaceSourceService(
   private val root: Path,
) : WorkspaceSourceService {

   companion object {
      class Factory : WorkspaceSourceServiceFactory {
         override fun build(params: InitializeParams, client: LanguageClient): WorkspaceSourceService {
            val rootUri = params.rootUri
            val root = File(URI.create(SourceNames.normalize(rootUri)))
            require(root.exists()) { "Fatal error - the workspace root location ($rootUri) doesn't appear to exist" }

            return FileBasedWorkspaceSourceService(root.toPath())
         }
      }
   }

   protected fun loadTaxiPackages(): List<TaxiPackageSources> {
      val taxiConfFiles = FileWorkspaceProjectFinder.findTaxiConfFiles(root)
      return taxiConfFiles.map { path ->
         val packageRootPath = if (path.isDirectory()) {
            path
         } else {
            path.parent
         }
         TaxiSourcesLoader.loadPackageAndDependencies(packageRootPath)
      }
   }

   override fun loadSources(): List<Pair<TaxiPackageProject?, Sequence<SourceCode>>> {
      val packages = loadTaxiPackages()
      // MP: 11-Feb-25: Support multiple projects in workspace.
      return if (packages.isEmpty()) {
         listOf(null to loadAllTaxiFilesUnderRoot())
      } else {
         packages.map {
            it.project to it.sources.asSequence()
         }
      }
   }

   override fun loadProjects(): List<TaxiPackageProject> {
      val taxiPackageSources = loadTaxiPackages()
      return taxiPackageSources.map { it.project }
   }

   protected fun loadAllTaxiFilesUnderRoot(): Sequence<SourceCode> {
      return root.toFile()
         .walk()
         .filter { it.extension == "taxi" && !it.isDirectory }
         .map { file -> SourceCode.from(file) }
   }
}
