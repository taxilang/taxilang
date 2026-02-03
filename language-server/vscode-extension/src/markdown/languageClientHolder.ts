/**
 * Global holder for the language client instance
 * This allows the markdown-it plugin to access the client for making LSP calls
 */

import { LanguageClient } from "vscode-languageclient/node";

let languageClient: LanguageClient | null = null;

export function setLanguageClient(client: LanguageClient) {
   languageClient = client;
}

export function getLanguageClient(): LanguageClient | null {
   return languageClient;
}
