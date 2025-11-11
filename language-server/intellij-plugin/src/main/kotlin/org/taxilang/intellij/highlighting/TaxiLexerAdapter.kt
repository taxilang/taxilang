package org.taxilang.intellij.highlighting

import lang.taxi.TaxiLexer
import org.antlr.intellij.adaptor.lexer.ANTLRLexerAdaptor
import org.taxilang.intellij.TaxiLanguage

/**
 * Simple adapter that bridges ANTLR's generated [TaxiLexer]
 * with IntelliJ's [Lexer] interface.
 *
 * Since the Taxi grammar now uses `WS -> channel(HIDDEN)` instead of `-> skip`,
 * ANTLR produces a continuous token stream suitable for IntelliJ.
 *
 * Therefore we can rely entirely on the official ANTLR4 IntelliJ adaptor.
 */
class TaxiLexerAdapter : ANTLRLexerAdaptor(TaxiLanguage, TaxiLexer(null)) {
}
