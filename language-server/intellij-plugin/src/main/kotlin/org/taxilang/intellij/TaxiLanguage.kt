package org.taxilang.intellij

import com.intellij.lang.Language

/**
 * Taxi language definition
 */
object TaxiLanguage : Language("Taxi") {
    override fun getDisplayName(): String = "Taxi"

    override fun isCaseSensitive(): Boolean = true
}
