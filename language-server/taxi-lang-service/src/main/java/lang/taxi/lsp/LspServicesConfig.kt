package lang.taxi.lsp

import lang.taxi.lsp.actions.CodeActionService
import lang.taxi.lsp.completion.CompletionService
import lang.taxi.lsp.completion.CompositeCompletionService
import lang.taxi.lsp.formatter.FormatterService
import lang.taxi.lsp.gotoDefinition.GotoDefinitionService
import lang.taxi.lsp.hover.HoverService
import lang.taxi.lsp.linter.LintingService
import lang.taxi.lsp.signatures.SignatureHelpService

/**
 * A set of all the services required by the Taxi language service.
 * Where possible, reasonable defaults are provided.
 */
data class LspServicesConfig(
    val compilerService: TaxiCompilerService = TaxiCompilerService(),
    val completionService: CompletionService = CompositeCompletionService.withDefaults(compilerService.typeCompletionBuilder),
    val formattingService: FormatterService = FormatterService(),
    val gotoDefinitionService: GotoDefinitionService = GotoDefinitionService(compilerService.typeCompletionBuilder),
    val hoverService: HoverService = HoverService(),
    val codeActionService: CodeActionService = CodeActionService(),
    val signatureHelpService: SignatureHelpService = SignatureHelpService(),
    val lintingService: LintingService = LintingService()
)
