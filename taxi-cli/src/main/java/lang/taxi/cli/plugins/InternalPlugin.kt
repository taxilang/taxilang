package lang.taxi.cli.plugins

import lang.taxi.plugins.Artifact
import lang.taxi.plugins.ArtifactId

/**
 * A InternalPlugin is one that's built in to Shipman -- ie.,
 * is packaged with the core JAR.
 */
interface InternalPlugin : Plugin {

    /**
     * Return the artifact which this plugin satisfies.
     */
    val artifact: Artifact

    override val id: ArtifactId
        get() = artifact.id
}
