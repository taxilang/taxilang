package lang.taxi.cli.plugins

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import lang.taxi.cli.utils.concat
import lang.taxi.cli.utils.log
import lang.taxi.plugins.Artifact
import org.apache.commons.io.FileUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import java.io.File
import java.net.URI

interface ExternalPluginProvider {
    fun resolvePlugins(pluginIdentifiers: List<Artifact>): Map<Artifact, File>
}

// Will move this elsehwere when the server is a thing
private object ContentTypes {
    const val TAXI_PLUGIN = "taxi-plugin"
}

class RemoteExternalPluginProvider(val repositories: List<URI>, val pluginsDirectory: File, val httpClient: HttpClient = HttpClientBuilder.create().build()) : ExternalPluginProvider {
    override fun resolvePlugins(pluginIdentifiers: List<Artifact>): Map<Artifact, File> {
        val artifacts: Map<Artifact, File> = this.pluginUrls(pluginIdentifiers).map { pair ->
            val (artifact, uriList) = pair
            if (artifact.isAbsolute() && resolvePluginLocation(artifact).exists()) {
                artifact to resolvePluginLocation(artifact)
            } else {
                val plugin = downloadPlugin(uriList, artifact)
                if (plugin == null) {
                    log().info("$artifact could not be downloaded from any of $uriList")
                }
                artifact to downloadPlugin(uriList, artifact)
            }
        }.filter { pair -> pair.second != null }
                // pair is effectively now artifact,file.
                // Annoying the null checker in Kotlin doesn't detect this.
                .map { pair ->
                    val (artifact, file) = pair
                    artifact to file!!
                }
                .toMap()
        return artifacts
    }

    private fun downloadPlugin(uris: Collection<URI>, artifact: Artifact): File? {
        for (uri in uris) {
            log().debug("Downloading $uri")
            try {
                val httpResponse = httpClient.execute(HttpGet(uri))
                if (httpResponse.statusLine.statusCode == 200) {
                    val header = httpResponse.getHeaders(PluginHttpHeaders.ARTIFACT_ID)[0]
                    val pluginLocation = resolvePluginLocation(Artifact.parse(header.value))
                    FileUtils.forceMkdir(pluginLocation)
                    val pluginFile = pluginLocation.resolve(httpResponse.getHeaders(PluginHttpHeaders.FILENAME)[0].value)
                    FileUtils.copyInputStreamToFile(httpResponse.entity.content, pluginFile)
                    log().info("Downloaded $artifact from $uri to $pluginLocation")
                    return pluginFile
                } else {
                    log().info("Download of $uri failed: Response code ${httpResponse.statusLine.statusCode}")
                }
            } catch (e: Exception) {
                log().info("Download of $uri failed: ${e.message}")
            }
        }
        log().debug("Artifact $artifact not downloaded, tried the following locations: ${uris.joinToString(",")}")
        return null
    }

    private fun resolvePluginLocation(artifact: Artifact): File {
        if (artifact.isAbsolute()) {
            return pluginsDirectory.resolve(artifact.path())
        }
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun <K, V, T> Multimap<K, V>.map(action: (Pair<K, Collection<V>>) -> T): List<T> {
        return this.keySet().map { key -> action(key to this.get(key)) }
    }


    fun pluginUrls(pluginIdentifiers: List<Artifact>): Multimap<Artifact, URI> {
        val result = ArrayListMultimap.create<Artifact, URI>()
        repositories.forEach { repo: URI ->
            pluginIdentifiers.forEach { artifact ->
                val uri = repo.concat("/store/repository/${ContentTypes.TAXI_PLUGIN}/${artifact.path()}")
                result.put(artifact, uri)
            }
        }
        return result
    }


}

class UnresolvedPluginsException(val artifacts: List<Artifact>) : RuntimeException("The following artifacts could not be downloaded: $artifacts")
class UnresolvedPluginException(val artifact: Artifact, uris: Collection<URI>) : RuntimeException("Couldn't download $artifact, tried the following locations: ${uris.joinToString(",")}")
