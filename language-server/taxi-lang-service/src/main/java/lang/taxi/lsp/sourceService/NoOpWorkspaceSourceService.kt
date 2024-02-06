package lang.taxi.lsp.sourceService

import lang.taxi.packages.TaxiPackageProject
import lang.taxi.sources.SourceCode
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.LanguageClient

/**
 * A source service that doesn't load any external sources.
 * This means only sources provided during the editing session
 * are considered.
 * Useful for web tooling not backed by any federation or schema discovery
 */
class NoOpWorkspaceSourceService : WorkspaceSourceService {
    companion object {
        class Factory : WorkspaceSourceServiceFactory {
            override fun build(params: InitializeParams, client: LanguageClient): WorkspaceSourceService {
                return NoOpWorkspaceSourceService()
            }

        }
    }

    override fun loadSources(resolveDependencies: Boolean): Sequence<SourceCode> {
        return emptySequence()
    }

    override fun loadProject(): TaxiPackageProject? {
        return null
    }
}
