package org.taxilang.packagemanager.transports

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.transport.AbstractTransporter
import org.eclipse.aether.spi.connector.transport.GetTask
import org.eclipse.aether.spi.connector.transport.PeekTask
import org.eclipse.aether.spi.connector.transport.PutTask
import org.eclipse.aether.spi.connector.transport.Transporter
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.taxilang.packagemanager.TaxiPackageBundler
import org.taxilang.packagemanager.layout.TaxiArtifactType
import org.taxilang.packagemanager.repository.git.copyPathToDestination
import lang.taxi.utils.log
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension

/**
 * A transport factory that follows a directory based layout on a local file system.
 * Great for testing.
 */
class TaxiFileSystemTransportFactory : TransporterFactory {
   override fun newInstance(session: RepositorySystemSession, remote: RemoteRepository): Transporter {
      return TaxiFileSystemTransport(session, remote)
   }

   override fun getPriority(): Float = 0F
}

class TaxiFileSystemTransport(private val session: RepositorySystemSession, private val remote: RemoteRepository) :
   AbstractTransporter() {

   override fun classify(error: Throwable): Int {
      return when (error) {
         is NoSuchFileException -> Transporter.ERROR_NOT_FOUND
         is java.nio.file.NoSuchFileException -> Transporter.ERROR_NOT_FOUND
         else -> {
            log().warn("Implement catch for error ${error::class.java.simpleName}")
            Transporter.ERROR_OTHER
         }
      }
   }

   override fun implPeek(task: PeekTask) {
      TODO("Not yet implemented")
   }

   override fun implGet(task: GetTask) {
      val path = Paths.get(task.location.path)
      val filePath = when (path.fileName.extension) {
         TaxiArtifactType.TAXI_CONF_FILE.extension -> path.parent.resolve("taxi.conf")
         TaxiArtifactType.TAXI_PROJECT_BUNDLE.extension -> {
            path.changeExtension(TaxiPackageBundler.EXTENSION)
         }
         else -> path
      }
      val file = Paths.get(remote.url).resolve(filePath)
      task.copyPathToDestination(file)
   }

   override fun implPut(task: PutTask) {
      TODO("Not yet implemented")
   }

   override fun implClose() {
   }


}

/**
 * Returns a new path reference with a modified extension.
 * Does not affect the underlying file.
 */
fun Path.changeExtension(newExtension: String):Path {
   val fileName = this.fileName.toString()
   val newFileName = if (fileName.contains('.')) {
      fileName.substringBeforeLast('.') + "." + newExtension
   } else {
      "$fileName.$newExtension"
   }
   return this.resolveSibling(newFileName)
}
