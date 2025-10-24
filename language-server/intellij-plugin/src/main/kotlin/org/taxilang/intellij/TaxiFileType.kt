package org.taxilang.intellij

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * File type for Taxi language files (.taxi extension)
 */
class TaxiFileType private constructor() : LanguageFileType(TaxiLanguage) {

    override fun getName(): String = "Taxi"

    override fun getDescription(): String = "Taxi language file"

    override fun getDefaultExtension(): String = "taxi"

    override fun getIcon(): Icon? = TaxiIcons.FILE

    companion object {
        @JvmStatic
        val INSTANCE = TaxiFileType()
    }
}
