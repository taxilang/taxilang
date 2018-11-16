package lang.taxi.cli.plugins.internal

import lang.taxi.cli.plugins.InternalPlugin
import lang.taxi.cli.utils.log
import lang.taxi.plugins.Artifact
import lang.taxi.plugins.ArtifactId
import lang.taxi.plugins.PluginWithConfig
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.io.File
import java.time.Instant


/**
 * Plugin which publishes other plugins
 */
@Component
class PublishPluginPlugin(
        private val restTemplate: RestTemplate = RestTemplate()
) : InternalPlugin, PluginWithConfig<PublishPluginConfig> {
    override val artifact = Artifact.parse("publish")
    private lateinit var config: PublishPluginConfig
    override fun setConfig(config: PublishPluginConfig) {
        this.config = config
    }


    fun publish() {
//        val zip = createZip()
        val fileToRelease = File(config.file)
        require(fileToRelease.exists()) { "File ${fileToRelease.canonicalPath} doesn't exist" }

        log().info("Publishing plugin ${config.id} from ${fileToRelease.canonicalPath} to ${config.taxiHub}")
        val releaseType = ReleaseType.parse(config.version)
        val releaseUrlParam = if (releaseType != null) "?releaseType=$releaseType" else "/${config.version}"
        val url = "${config.taxiHub}/projects/${config.id.group}/${config.id.name}/releases$releaseUrlParam"

        upload(fileToRelease, url)
    }

    private fun upload(fileToRelease: File, url: String) {
        val map: LinkedMultiValueMap<String, Any> = LinkedMultiValueMap()
        map.put("file", listOf(UrlResource(fileToRelease.toURI())))
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val requestEntity = HttpEntity(map, headers)

        try {
            val result = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Release::class.java)
            val release = result.body
            log().info("Released version ${release.version} with id of ${release.identifier}")
        } catch (error: HttpClientErrorException) {
            log().error("Failed to upload", error.message)
        }
    }


//    private fun createZip(): File {
//        val zipFilePath = File.createTempFile(config.id.fileSafeIdentifier, ".zip")
//        val zipFile = ZipFile(zipFilePath)
//        val matcher = FileSystems.getDefault().getPathMatcher("glob:${config.include}")
//        File(config.artifactDir)
//                .walkTopDown()
//                .filter { matcher.matches(it.toPath().fileName) }
//                .forEach {
//                    if (it.isFile) {
//                        log().info("Adding file ${it.canonicalPath}")
//                        val zipParameters = ZipParameters()
//                        zipParameters.compressionMethod = Zip4jConstants.COMP_DEFLATE
//                        zipParameters.compressionLevel = Zip4jConstants.DEFLATE_LEVEL_NORMAL
//                        zipFile.addFile(it, zipParameters)
//                    }
//                }
//        return zipFilePath
//    }


}

data class Release(
        val identifier: String,
        val version: String)

data class PublishPluginConfig(val taxiHub: String,
                               val file: String,
                               val id: ArtifactId,
        // Note : Can be a number or a ReleaseType
                               val version: String
)

enum class ReleaseType {
    MAJOR, MINOR, PATCH;

    companion object {
        fun parse(value: String): ReleaseType? {
            return if (ReleaseType.values().map { it.name }.contains(value.toUpperCase())) {
                valueOf(value.toUpperCase());
            } else {
                null
            }
        }
    }

}