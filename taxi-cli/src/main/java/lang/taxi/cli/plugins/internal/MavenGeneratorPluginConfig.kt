package lang.taxi.cli.plugins.internal

data class MavenGeneratorPluginConfig(
   val groupId: String,
   val artifactId: String,
   val modelVersion: String = "4.0.0",
   val dependencies: List<Dependency> = emptyList(),
   val repositories: List<Repository> = emptyList(),
   val distributionManagement: DistributionManagement?
)

data class Repository(
   val id: String,
   val name: String = id,
   val url: String,
   val snapshots: Boolean = false,
   val releases: Boolean = true
)

data class Dependency(
   val groupId: String,
   val artifactId: String,
   val version: String
)

data class DistributionManagement(
   val id: String,
   val url: String
)
