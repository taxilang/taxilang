package org.taxilang.intellij.highlighting

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Factory for creating Taxi syntax highlighters
 */
class TaxiSyntaxHighlighterFactory : SyntaxHighlighterFactory() {

   override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
      return TaxiSyntaxHighlighter()
   }
}
