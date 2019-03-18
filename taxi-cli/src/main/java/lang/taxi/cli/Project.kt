package lang.taxi.cli

import com.typesafe.config.Config
import lang.taxi.plugins.Artifact

data class TaxiProject(
        val sourceFolder: String = "src",
        val outputFolder: String = "target",
        val plugins: Map<String, Config> = emptyMap()) {
    val pluginArtifacts: Map<Artifact, Config> by lazy {
        this.plugins.mapKeys { (key, _) -> Artifact.parse(key) }
    }
}