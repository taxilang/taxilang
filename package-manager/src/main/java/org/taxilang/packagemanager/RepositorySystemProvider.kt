package org.taxilang.packagemanager

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.ArtifactDescriptorReader
import org.eclipse.aether.impl.VersionResolver
import org.eclipse.aether.internal.impl.LocalPathComposer
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.taxilang.packagemanager.layout.TaxiDescriptorReader
import org.taxilang.packagemanager.layout.TaxiLocalPathComposer
import org.taxilang.packagemanager.repository.git.GitProjectLayoutFactory
import org.taxilang.packagemanager.repository.git.GitRepoTransportFactory
import org.taxilang.packagemanager.repository.git.GitVersionResolver
import org.taxilang.packagemanager.transports.TaxiFileSystemTransportFactory

object RepositorySystemProvider {
   fun build(): Pair<RepositorySystem, RepositorySystemSession> {
      return build(
         listOf(
            TaxiFileSystemTransportFactory::class.java,
            GitRepoTransportFactory::class.java,
         )
      )
   }

   fun build(transports: List<Class<out TransporterFactory>>): Pair<RepositorySystem, RepositorySystemSession> {
      val serviceLocator = MavenRepositorySystemUtils.newServiceLocator()
      transports.forEach { serviceLocator.addService(TransporterFactory::class.java, it) }
      serviceLocator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
      serviceLocator.addService(RepositoryLayoutFactory::class.java, GitProjectLayoutFactory::class.java)
      serviceLocator.setService(ArtifactDescriptorReader::class.java, TaxiDescriptorReader::class.java)
      serviceLocator.setService(LocalPathComposer::class.java, TaxiLocalPathComposer::class.java)
      serviceLocator.setService(VersionResolver::class.java, GitVersionResolver::class.java)
      val repositorySystem = serviceLocator.getService(RepositorySystem::class.java)
      val session = MavenRepositorySystemUtils.newSession()
      return repositorySystem to session
   }
}
