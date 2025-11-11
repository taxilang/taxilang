package org.taxilang.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icons for the Taxi plugin
 */
object TaxiIcons {
    /**
     * File icon for .taxi files
     * Falls back to default language icon if custom icon is not available
     */
    @JvmStatic
    val FILE: Icon = loadIcon("/icons/taxi-file.svg")

    private fun loadIcon(path: String): Icon {
        return try {
            IconLoader.getIcon(path, TaxiIcons::class.java)
        } catch (e: Exception) {
            // Fallback to a default icon if our custom icon is not found
            IconLoader.getIcon("/fileTypes/text.svg", TaxiIcons::class.java)
        }
    }
}
