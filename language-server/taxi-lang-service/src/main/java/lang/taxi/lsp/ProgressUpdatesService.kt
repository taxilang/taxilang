package lang.taxi.lsp

import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressKind
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.services.LanguageClient

/**
 * A simple service which handles sending progress updates to the LSP Client
 */
class ProgressUpdatesService(private val client: LanguageClient) {
   fun handleEvent(event: ProgressParams) {
      val notification: WorkDoneProgressNotification = event.value.left
      if (notification.kind == WorkDoneProgressKind.begin) {
         client.createProgress(WorkDoneProgressCreateParams().apply {
            token = event.token
         })
      }
      client.notifyProgress(event)
   }
}
