package lang.taxi.lsp.workspace

import com.orbitalhq.asTaxiSource
import com.orbitalhq.schemaServer.core.adaptors.taxi.TaxiSchemaSourcesAdaptor
import com.orbitalhq.schemaServer.core.file.FileProjectSpec
import com.orbitalhq.schemaServer.core.file.packages.FileSystemPackageLoader
import com.orbitalhq.utils.files.FileSystemChangeEvent
import com.orbitalhq.utils.files.ReactiveFileSystemMonitor
import com.orbitalhq.utils.files.ReactivePollingFileSystemMonitor
import lang.taxi.lsp.sourceService.FileBasedWorkspaceSourceService
import lang.taxi.lsp.sourceService.WorkspaceSourceService
import lang.taxi.lsp.sourceService.WorkspaceSourceServiceFactory
import lang.taxi.packages.TaxiPackageProject
import lang.taxi.packages.TaxiPackageSources
import lang.taxi.sources.SourceCode
import lang.taxi.types.SourceNames
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient
import org.taxilang.packagemanager.DefaultDependencyFetcherProvider
import org.taxilang.packagemanager.DependencyFetcherProvider
import org.taxilang.packagemanager.NoOpDependencyFetcherProvider
import reactor.core.publisher.Flux
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

/**
 * This is an extension of the FileBasedWorkspaceSourceService, but brings in support for
 * transpilation of code from other languages like Avro, OAS, etc.
 */
class TranspilingWorkspaceSourceService(
   root: Path,
   private val client: LanguageClient
) : FileBasedWorkspaceSourceService(root) {
   companion object {
      class Factory : WorkspaceSourceServiceFactory {
         override fun build(params: InitializeParams, client: LanguageClient): WorkspaceSourceService {
            val rootUri = params.rootUri
            val root = File(URI.create(SourceNames.normalize(rootUri)))
            require(root.exists()) { "Fatal error - the workspace root location ($rootUri) doesn't appear to exist" }
            return TranspilingWorkspaceSourceService(root.toPath(), client)
         }
      }
   }
   override fun loadSources(): List<Pair<TaxiPackageProject?, Sequence<SourceCode>>> {
      val packages = loadTaxiPackages()
      return if (packages.isEmpty()) {
         listOf(null to loadAllTaxiFilesUnderRoot())
      } else {
         packages.map {
            it.project to loadAndTranspilePackage(it)
         }

      }
   }

   private fun loadAndTranspilePackage(taxiPackage: TaxiPackageSources): Sequence<SourceCode> {
      if (taxiPackage.project.packageRootPath == null) {
         client.logMessage(MessageParams(MessageType.Info, "Project ${taxiPackage.project.identifier.id} does not have a package root path configured. This is unexpected. This project cannot be loaded"))
         return emptySequence()
      }
      val spec = FileProjectSpec(path = taxiPackage.project.packageRootPath!!)
      // Note: We don't need to resolve dependencies here, as they've already been resolved in the base class
      val converter = TaxiSchemaSourcesAdaptor(dependencyFetcherProvider = DefaultDependencyFetcherProvider)

      // Using a NoOpFileSystemMonitor here, as file watching is handled by the LSP - we don't need to do it.
      val packageLoader = FileSystemPackageLoader(spec, converter, NoOpFileSystemMonitor)
      val packageMetadata = converter.buildMetadata(packageLoader)
         .block()!!
      val sourcePackage = converter.convert(packageMetadata, packageLoader).block()!!
      return sourcePackage.sources
         .map {
            val uriPath = Paths.get(it.pathOrName.toUri())
            val withFullPath = it.asTaxiSource()
               .copy(sourceName = uriPath.toUri().toASCIIString(), path = uriPath)
            withFullPath

         }
         .asSequence()
   }
}

private object NoOpFileSystemMonitor : ReactiveFileSystemMonitor {
   override fun startWatching(): Flux<List<FileSystemChangeEvent>> = Flux.empty()

   override fun suspend() {
   }

   override fun resume() {
   }

   override fun stop() {
   }

}
