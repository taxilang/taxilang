package org.taxilang.intellij.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.taxilang.intellij.TaxiFileType
import javax.swing.Icon

class TaxiColorSettingsPage : ColorSettingsPage {
   private val DEMO_TEXT = """
import com.other.SomeType

namespace com.example


[[This is a documentation comment]]
// This is a line comment
/* This is a block comment */


type alias UserId as String

model Person {
      firstName: String
      lastName: String
      age: Int
      isActive: Boolean = true
}

type Customer inherits Person {
      customerId: UserId
      totalSpent: Decimal
}

service CustomerService {
      operation findCustomer(UserId): Customer
      write operation updateCustomer(Customer): Customer
}

query CustomerQuery {
      given { customerId: UserId = 'ABC' }
      find { Customer }
      as {
         id: UserId = customerId
         name: String = firstName + " " + lastName
         category: String = when {
            totalSpent > 1000 -> "Premium"
            totalSpent > 500 -> "Standard"
            else -> "Basic"
         }
      }[]
}
"""
   // Reference the text attribute keys from the highlighter
   private val KEYWORD = TextAttributesKey.createTextAttributesKey(
      "TAXI_KEYWORD",
      DefaultLanguageHighlighterColors.KEYWORD
   )

   private val STRING = TextAttributesKey.createTextAttributesKey(
      "TAXI_STRING",
      DefaultLanguageHighlighterColors.STRING
   )

   private val NUMBER = TextAttributesKey.createTextAttributesKey(
      "TAXI_NUMBER",
      DefaultLanguageHighlighterColors.NUMBER
   )

   private val LINE_COMMENT = TextAttributesKey.createTextAttributesKey(
      "TAXI_LINE_COMMENT",
      DefaultLanguageHighlighterColors.LINE_COMMENT
   )

   private val BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey(
      "TAXI_BLOCK_COMMENT",
      DefaultLanguageHighlighterColors.BLOCK_COMMENT
   )

   private val DOC_COMMENT = TextAttributesKey.createTextAttributesKey(
      "TAXI_DOC_COMMENT",
      DefaultLanguageHighlighterColors.DOC_COMMENT
   )

   private val OPERATOR = TextAttributesKey.createTextAttributesKey(
      "TAXI_OPERATION_SIGN",
      DefaultLanguageHighlighterColors.OPERATION_SIGN
   )

   private val PARENTHESES = TextAttributesKey.createTextAttributesKey(
      "TAXI_PARENTHESES",
      DefaultLanguageHighlighterColors.PARENTHESES
   )

   private val IDENTIFIER = TextAttributesKey.createTextAttributesKey(
      "TAXI_IDENTIFIER",
      DefaultLanguageHighlighterColors.IDENTIFIER
   )

   private val DESCRIPTORS = arrayOf(
      AttributesDescriptor("Keywords", KEYWORD),
      AttributesDescriptor("Strings", STRING),
      AttributesDescriptor("Numbers", NUMBER),
      AttributesDescriptor("Line comments", LINE_COMMENT),
      AttributesDescriptor("Block comments", BLOCK_COMMENT),
      AttributesDescriptor("Documentation", DOC_COMMENT),
      AttributesDescriptor("Operators", OPERATOR),
      AttributesDescriptor("Parentheses", PARENTHESES),
      AttributesDescriptor("Identifiers", IDENTIFIER)
   )

   override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

   override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

   override fun getDisplayName(): String = "Taxi"

   override fun getIcon(): Icon = TaxiFileType.icon
   override fun getHighlighter(): SyntaxHighlighter = TaxiSyntaxHighlighter()

   override fun getDemoText(): String = DEMO_TEXT

   override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey>? = null
}
