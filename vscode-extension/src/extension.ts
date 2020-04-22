// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import {commands, OutputChannel, workspace} from 'vscode';
import * as vscode from 'vscode';
import * as path from 'path';
import * as WebSocket from 'ws';

import {LanguageClient, LanguageClientOptions, ServerOptions, TransportKind} from 'vscode-languageclient';


// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {

    // Use the console to output diagnostic information (console.log) and errors (console.error)
    // This line of code will only be executed once when your extension is activated
    console.log('Congratulations, your extension "taxi-language-server" is now active!');

    const socketPort = workspace.getConfiguration('taxiLanguageServer').get('port', 7000);
    let socket: WebSocket | null = null;
    console.log(`taxiLanguageServer streaming port is configured to ${socketPort}`);
    // commands.registerCommand('taxiLanguageServer.startStreaming', () => {
        // Establish websocket connection
	socket = new WebSocket(`ws://localhost:${socketPort}`);
    // });

    // Get the java home from the process environment.
    const {JAVA_HOME} = process.env;
    // If java home is available continue.
    if (!JAVA_HOME) {
        console.error('No JAVA_HOME found, so cannot continue');
        return;
    }
    if (JAVA_HOME) {
        console.log(`Using java from JAVA_HOME: ${JAVA_HOME}`);
        // Java execution path.
        let excecutable: string = path.join(JAVA_HOME, 'bin', 'java');

        // path to the launcher.jar
        let classPath = path.join(__dirname, 'taxi-lang-server.jar');
        const debugSettings = '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005,quiet=y';
        const args: string[] = [debugSettings,'-cp', classPath];
        console.log(JSON.stringify(args));

        // Set the server options
        // -- java execution path
        // -- argument to be pass when executing the java command
        // Name of the launcher class which contains the main.
        const main: string = 'lang.taxi.lsp.Launcher';

        let serverOptions: ServerOptions = {
            command: excecutable,
            args: [...args, main],
            options: {}
        };

        // The log to send
        let log = '';
        const websocketOutputChannel: OutputChannel = {
            name: 'websocket',
            // Only append the logs but send them later
            append(value: string) {
                log += value;
                console.log(value);
            },
            appendLine(value: string) {
                log += value;
                // Don't send logs until WebSocket initialization
                if (socket && socket.readyState === WebSocket.OPEN) {
                    socket.send(log);
                }
                log = '';
            },
            clear() {
            },
            show() {
            },
            hide() {
            },
            dispose() {
            }
        };

        // Options to control the language client
        let clientOptions: LanguageClientOptions = {
            // Register the server for plain text documents
            documentSelector: [{scheme: 'file', language: 'taxi'}],
            synchronize: {
                // Notify the server about file changes to .taxi files contained in the workspace
                fileEvents: workspace.createFileSystemWatcher('**/*.taxi')
            },
            // Hijacks all LSP logs and redirect them to a specific port through WebSocket connection
            outputChannel: websocketOutputChannel
        };

        // Create the language client and start the client.
        let disposable = new LanguageClient('Taxi', 'Taxi Language Server', serverOptions, clientOptions).start();

        // Disposables to remove on deactivation.
        context.subscriptions.push(disposable);
    }
}

// this method is called when your extension is deactivated
export function deactivate() {
    console.log("taxi-language-server is deactivated");
}
