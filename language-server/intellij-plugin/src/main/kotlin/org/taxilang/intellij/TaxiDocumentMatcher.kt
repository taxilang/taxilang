package org.taxilang.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.DocumentMatcher

/**
 * Matches Taxi files to the Taxi language server
 */
class TaxiDocumentMatcher : DocumentMatcher {
    override fun match(file: VirtualFile, project: Project): Boolean {
        return file.extension == "taxi" || file.name == "taxi.conf"
    }
}
