package lang.taxi.packages

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import lang.taxi.packages.repository.PackageService
import lang.taxi.packages.repository.PackageServiceFactory
import lang.taxi.packages.utils.log
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.message.BasicHttpResponse
import org.apache.http.message.BasicStatusLine
import java.io.File
import java.io.InputStream
import java.nio.file.Path

class SimpleFilePackageService(private val repositoryRoot: Path) : PackageService {
   companion object {
      fun fileServiceFactory(repositoryRoot: Path): Pair<SimpleFilePackageService, PackageServiceFactory> {
         val service = SimpleFilePackageService(repositoryRoot)
         val serviceFactory = mock<PackageServiceFactory> {
            on { get(any(), any()) } doReturn service
         }
         return service to serviceFactory
      }
   }

   override fun upload(zip: File, project: TaxiPackageProject): HttpResponse {
      val destination = repositoryRoot.resolve(project.identifier.fileSafeIdentifier + ".zip").toFile()
      destination.createNewFile()
      IOUtils.copy(zip.inputStream(), destination.outputStream())

      log().info("Copied zip to ${destination.canonicalPath}")

      return BasicHttpResponse(BasicStatusLine(ProtocolVersion("http", 1, 1), 200, "OK"))
   }

   override fun attemptDownload(identifier: PackageIdentifier, userFacingLogger: MessageLogger): InputStream? {
      val file = repositoryRoot.resolve(identifier.fileSafeIdentifier + ".zip").toFile()
      return if (file.exists()) {
         file.inputStream()
      } else {
         null
      }

   }

   override val repositoryType: String = "file"
}
