package lang.taxi.lsp.sourceService

import lang.taxi.packages.TaxiPackageProject
import lang.taxi.sources.SourceCode
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.LanguageClient

/**
 * Responsible for providing a list of URIs and their
 * source content, which is fed to the compiler.
 *
 * If working with a VSCode instance, the FileBasedWorkspaceSourceService
 * is what you want.
 *
 * If you're working in a web environment with multiple clients, you're gonna
 * need something more robust, which can handle multiple active editors
 */
interface WorkspaceSourceService {
   /**
    * Returns all the sources for the project in the workspace.
    * If there are dependencies expressed in the taxi.conf file, these are resolved
    */
    fun loadSources(resolveDependencies: Boolean = true): Sequence<SourceCode>

    /**
     * Returns a TaxiPackageProject if one exists within the workspace.
     * In some cases - such as editors running without a taxi.conf,
     * or in schema federated services, the project doesn't exist.
     *
     * If returned, the dependencies are examined and loaded so they
     * are included in the source.
     */
    fun loadProject(): TaxiPackageProject?
}

interface WorkspaceSourceServiceFactory {
    fun build(params: InitializeParams, client:LanguageClient): WorkspaceSourceService
}

