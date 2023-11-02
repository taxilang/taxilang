package lang.taxi.lsp

import com.google.common.io.Resources
import lang.taxi.lsp.sourceService.FileBasedWorkspaceSourceService
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.util.Positions
import org.mockito.kotlin.mock
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

fun documentServiceFor(rootResourcePath: String): Pair<TaxiTextDocumentService, Path> {
   val workspaceUri = Resources.getResource(rootResourcePath)
   return documentServiceFor(workspaceUri)
}

fun documentServiceFor(
   workspaceUri: URL,
   config: LspServicesConfig = LspServicesConfig()
): Pair<TaxiTextDocumentService, Path> {
   val workspaceRoot = Paths.get(workspaceUri.toURI())
   val service = TaxiTextDocumentService(config)
   val languageClient: LanguageClient = mock { }
   service.connect(languageClient)

   val sourceServiceFactory = FileBasedWorkspaceSourceService.Companion.Factory()
   val initializeParams = InitializeParams().apply {
      rootUri = workspaceUri.toString()
   }
   service.initialize(initializeParams, sourceServiceFactory.build(initializeParams, languageClient))

   return service to workspaceRoot
}

typealias Filename = String
typealias DocumentText = String

fun documentServiceWithFiles(
   directory: File,
   config: LspServicesConfig = LspServicesConfig(),
   vararg files: Pair<Filename, DocumentText>
): Pair<TaxiTextDocumentService, Path> {
   files.forEach { (filename, text) ->
      val file = directory.resolve(filename)
      file.createNewFile()
      file.writeText(text)
   }
   return documentServiceFor(directory.toURI().toURL(), config)
}

fun Path.versionedDocument(name: String, version: Int = 1): VersionedTextDocumentIdentifier {
   return VersionedTextDocumentIdentifier(
      this.resolve(name).toUri().toString(),
      version
   )
}

fun Path.document(name: String): TextDocumentIdentifier {
   return TextDocumentIdentifier(this.resolve(name).toUri().toString())
}

private typealias LineIndex = Int
private typealias CharIndex = Int

enum class CursorPosition {
   StartOfText,
   EndOfText
}

fun String.positionOf(match: String, cursorPosition: CursorPosition = CursorPosition.StartOfText): Pair<LineIndex, CharIndex> {
   val cursorPositionLine = this.lines().indexOfFirst { it.contains(match) }
   val cursorPositionChar = this.lines()[cursorPositionLine].indexOf(match).let { indexOfStart ->
      when (cursorPosition) {
         CursorPosition.StartOfText -> indexOfStart
         CursorPosition.EndOfText -> indexOfStart + match.length
      }
   }
   return cursorPositionLine to cursorPositionChar
}

fun Pair<LineIndex, CharIndex>.toPosition():Position = Position(first, second)
