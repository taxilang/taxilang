package lang.taxi.cli.plugins.internal

import com.typesafe.config.Config

data class MavenGeneratorPluginConfig(
   val modelVersion: String,
   val groupId: String,
   val artifactId: String,
   val version: String,
   val dependencies: List<Config>,
   val distributionManagement: DistributionManagement
)

data class DistributionManagement(
   val url: String
)
