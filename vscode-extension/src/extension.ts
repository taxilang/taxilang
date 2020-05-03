// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import {commands, OutputChannel, workspace} from 'vscode';
import * as vscode from 'vscode';
import * as path from 'path';
import * as WebSocket from 'ws';
import * as findJavaHome from 'find-java-home';

import {LanguageClient, LanguageClientOptions, ServerOptions, TransportKind} from 'vscode-languageclient';


// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {

    // Use the console to output diagnostic information (console.log) and errors (console.error)
    // This line of code will only be executed once when your extension is activated
    console.log('Taxi language extension starting');


    const definedJavaHome = workspace.getConfiguration('taxi').get('javaHome', '');
    if (definedJavaHome !== '') {
        startPlugin(definedJavaHome, context);
    } else {
        findJavaHome({allowJre: true}, (err, home) => {
            if (err) {
                return console.log(err);
            }
            startPlugin(home, context);
        });
    }

}

function startPlugin(javaHome: string, context: vscode.ExtensionContext) {
    const useDebugJar = workspace.getConfiguration('taxi').get('useDevJar', false);
    const enableDebug = workspace.getConfiguration('taxi').get('enableDebugSession', false);
    if (javaHome) {
        console.log(`Using java from ${javaHome}`);
        // Java execution path.
        let excecutable: string = path.join(javaHome, 'bin', 'java');

        // path to the launcher.jar

        //  /home/marty/dev/taxi-lang-server/taxi-lang-server-standalone/target/dependency/*.jar
        //   java -classpath "./classes:./dependency/*" lang.taxi.lsp.Launcher
        let classPath:string;
        if (useDebugJar) {
            const classPathSeperator = (process.platform === "win32") ? ';' : ':';
            classPath = [ 
                path.join(__dirname, '..', '..','..','taxi-lang','compiler', 'target', 'classes') ,
                path.join(__dirname, '..', '..', 'taxi-lang-service', 'target', 'classes') ,
                path.join(__dirname, '..', '..', 'taxi-lang-server-standalone', 'target', 'classes') ,
                path.join(__dirname, '..', '..', 'taxi-lang-server-standalone', 'target', 'dependency', '*')  
            ].join(classPathSeperator);
        } else {
            const jarName = 'taxi-lang-server-standalone.jar';
            classPath = path.join(__dirname, jarName);
        }

        // let classPath = (useDebugJar) ? path.join(__dirname, '..', '..', 'taxi-lang-server-standalone', 'target', jarName) : path.join(__dirname, jarName);
        const debugSettings = (enableDebug) ? ['-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005,quiet=y'] : [];
        const args: string[] = debugSettings.concat(['-cp', classPath]);
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

        // Options to control the language client
        let clientOptions: LanguageClientOptions = {
            // Register the server for plain text documents
            documentSelector: [{scheme: 'file', language: 'taxi'}],
            synchronize: {
                // Notify the server about file changes to .taxi files contained in the workspace
                fileEvents: workspace.createFileSystemWatcher('**/*.taxi')
            },
            // Hijacks all LSP logs and redirect them to a specific port through WebSocket connection
            // outputChannel: websocketOutputChannel
        };

        // Create the language client and start the client.
        let disposable = new LanguageClient('Taxi', 'Taxi Language Server', serverOptions, clientOptions).start();

        // Disposables to remove on deactivation.
        context.subscriptions.push(disposable);
    }
}

// this method is called when your extension is deactivated
export function deactivate() {
    console.log('taxi-language-server is deactivated');
}
