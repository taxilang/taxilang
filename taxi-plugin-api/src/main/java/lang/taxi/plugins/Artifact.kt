package lang.taxi.plugins

import com.github.zafarkhaja.semver.Version
import java.io.File

/**
 * The group & name of an artifact.
 * Excludes version, which may change over time
 */
data class ArtifactId(val group: String, val name: String) {
    override fun toString() = "@$group/$name"

    val fileSafeIdentifier = "$group.$name"
}

data class Artifact(val id: ArtifactId, val version: String) {
    constructor(group: String, name: String, version: String) : this(ArtifactId(group, name), version)

    val name = id.name
    val group = id.group

    companion object {
        val UNRESOLVED_VERSIONS = listOf("*", "latest")
        const val DEFAULT_GROUP = "taxi"
        const val DEFAULT_VERSION = "latest"

        @JvmStatic
        fun parse(value: String): Artifact {
            // TODO : I couldn't decide on splitting based on : and /
            // this is largely because this part was written whilst drunk on the tube.
            // so, check for both.
            val parts = if (value.contains("/")) {
                value.split("/")
            } else {
                value.split(":")
            }
            when {
                parts.size == 3 -> return Artifact(parts[0], parts[1], parts[2])
                parts.size == 1 -> return Artifact(DEFAULT_GROUP, parts[0], DEFAULT_VERSION)
                parts.size == 2 ->
                    return if (parts[1].isSemver()) Artifact(DEFAULT_GROUP, parts[0], parts[1])
                    else Artifact(parts[0], parts[1], DEFAULT_VERSION)
            }
            throw IllegalArgumentException("Unparsable artifact: $value")
        }

        private fun String.isSemver(): Boolean {
            try {
                Version.valueOf(this)
                return true
            } catch (e: Exception) {
                return false
            }
        }
    }

    val artifactId: ArtifactId = ArtifactId(this.group, this.name)

    fun satisfies(artifact: Artifact): Boolean {
        // TODO : Should ensure that this.version >= artifact, also considering
        // wildards
        return this.artifactId == artifact.artifactId
    }

    fun getIdentifier(): String = "$group/$name/$version"

    fun isAbsolute(): Boolean {
        return !UNRESOLVED_VERSIONS.contains(version.toLowerCase())
    }

    override fun toString(): String {
        return getIdentifier()
    }

    fun path(): String {
        return listOf(group, name, version).joinToString(File.separator)
    }
}
