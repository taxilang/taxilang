package org.taxilang.packagemanager

import org.apache.maven.model.building.ModelBuilder
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.apache.maven.repository.internal.ModelCacheFactory
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.impl.ArtifactDescriptorReader
import org.eclipse.aether.impl.ArtifactResolver
import org.eclipse.aether.impl.MetadataResolver
import org.eclipse.aether.impl.RemoteRepositoryManager
import org.eclipse.aether.impl.RepositoryEventDispatcher
import org.eclipse.aether.impl.VersionRangeResolver
import org.eclipse.aether.impl.VersionResolver
import org.eclipse.aether.internal.impl.LocalPathComposer
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.spi.synccontext.SyncContextFactory
import org.eclipse.aether.supplier.RepositorySystemSupplier
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.ChecksumExtractor
import org.taxilang.packagemanager.layout.TaxiDescriptorReader
import org.taxilang.packagemanager.layout.TaxiLocalPathComposer
import org.taxilang.packagemanager.repository.git.GitProjectLayoutFactory
import org.taxilang.packagemanager.repository.git.GitRepoTransportFactory
import org.taxilang.packagemanager.repository.git.GitVersionResolver
import org.taxilang.packagemanager.repository.nexus.NexusLayoutFactory
import org.taxilang.packagemanager.repository.nexus.NexusTransportFactory

object RepositorySystemProvider {
   fun build(workspaceReader: WorkspaceReader? = null): Pair<RepositorySystem, RepositorySystemSession> {
      return build(
         mapOf(
            FileTransporterFactory.NAME to FileTransporterFactory(),
            "NexusTransportFactory" to NexusTransportFactory(),
            "GItRepoTransportFactory" to GitRepoTransportFactory(),
         ),
         workspaceReader
      )
   }


//   val serviceLocator = MavenRepositorySystemUtils.newServiceLocator()
//      transports.forEach { serviceLocator.addService(TransporterFactory::class.java, it) }
//      serviceLocator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
//      serviceLocator.addService(RepositoryLayoutFactory::class.java, GitProjectLayoutFactory::class.java)
//      serviceLocator.addService(RepositoryLayoutFactory::class.java, NexusLayoutFactory::class.java)
//      serviceLocator.setService(ArtifactDescriptorReader::class.java, TaxiDescriptorReader::class.java)
//      serviceLocator.setService(LocalPathComposer::class.java, TaxiLocalPathComposer::class.java)
//      serviceLocator.setService(VersionResolver::class.java, GitVersionResolver::class.java)
//      val repositorySystem = serviceLocator.getService(RepositorySystem::class.java)
   fun build(transports: Map<String,TransporterFactory>, workspaceReader: WorkspaceReader? = null): Pair<RepositorySystem, RepositorySystemSession> {
      val session = MavenRepositorySystemUtils.newSession().let {
         if (workspaceReader != null) {
            it.setWorkspaceReader(workspaceReader)
         } else it
      }
      val repositorySystem = buildNew(transports)
      return repositorySystem to session
   }

//   fun build(transports: List<Class<out TransporterFactory>>): Pair<RepositorySystem, RepositorySystemSession> {
//      val serviceLocator = MavenRepositorySystemUtils.newServiceLocator()
//      transports.forEach { serviceLocator.addService(TransporterFactory::class.java, it) }
//      serviceLocator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
//      serviceLocator.addService(RepositoryLayoutFactory::class.java, GitProjectLayoutFactory::class.java)
//      serviceLocator.addService(RepositoryLayoutFactory::class.java, NexusLayoutFactory::class.java)
//      serviceLocator.setService(ArtifactDescriptorReader::class.java, TaxiDescriptorReader::class.java)
//      serviceLocator.setService(LocalPathComposer::class.java, TaxiLocalPathComposer::class.java)
//      serviceLocator.setService(VersionResolver::class.java, GitVersionResolver::class.java)
//      val repositorySystem = serviceLocator.getService(RepositorySystem::class.java)
//      val session = MavenRepositorySystemUtils.newSession()
//      return repositorySystem to session
//   }

   private fun buildNew(transports: Map<String,TransporterFactory>): RepositorySystem {
      return TaxiRepositorySystemSupplier(transports).get()
   }
}

class TaxiRepositorySystemSupplier(private val transportFactories: Map<String,TransporterFactory>) : RepositorySystemSupplier() {

   override fun getTransporterFactories(extractors: MutableMap<String, ChecksumExtractor>?): MutableMap<String, TransporterFactory> {
      val result =  transportFactories
      return result.toMutableMap()
   }

   override fun getArtifactDescriptorReader(
      remoteRepositoryManager: RemoteRepositoryManager?,
      versionResolver: VersionResolver,
      versionRangeResolver: VersionRangeResolver,
      artifactResolver: ArtifactResolver,
      modelBuilder: ModelBuilder?,
      repositoryEventDispatcher: RepositoryEventDispatcher?,
      modelCacheFactory: ModelCacheFactory?
   ): ArtifactDescriptorReader {
      return TaxiDescriptorReader(
         artifactResolver,
         versionResolver
      )
   }

   override fun getLocalPathComposer(): LocalPathComposer {
      return TaxiLocalPathComposer()
   }

   override fun getVersionResolver(
      metadataResolver: MetadataResolver?,
      syncContextFactory: SyncContextFactory?,
      repositoryEventDispatcher: RepositoryEventDispatcher?
   ): VersionResolver {
      return GitVersionResolver()
   }


   override fun getRepositoryLayoutFactories(checksumAlgorithmFactorySelector: ChecksumAlgorithmFactorySelector?): MutableMap<String, RepositoryLayoutFactory> {
      // Do not use reflection for providing these names, as we use this code in GraalVM native images,
      // and it complicates the process.
      val result = mapOf(
         "TaxiGitProjectLayoutFactory" to GitProjectLayoutFactory(),
         "NexusLayoutFactory" to NexusLayoutFactory(),
      ) + super.getRepositoryLayoutFactories(checksumAlgorithmFactorySelector)
      return result.toMutableMap()
   }
}
