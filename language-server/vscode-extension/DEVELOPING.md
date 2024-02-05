## Building / Developing this plugin

## Developing the grammar

* Have to use a seperate grammar format for VsCode (textmate grammar) and Web / Monaco.

The `typescript.tmLanguage.json` tm grammar is currently being used as reference, and is checked in.

Easiest way to test grammar:

Install [vscode-tmgrammar-snap](https://github.com/PanAeon/vscode-tmgrammar-test)

```bash
npm i -g vscode-tmgrammar-snap
```

Modify `taxi.tmLanguage.json`, then run this from the `vscode-extension` directory:

```bash
vscode-tmgrammar-snap -u "./src/test/grammar/snap/*.taxi"
```

The file in `src/test/grammar/snap/sample.taxi.snap` shows the parse result.

## Debugging the VSCode extension
Getting a full debug session running can be tricky.

### Configuring IntelliJ
In Run > Edit Configurations, add a new Remote JVM Debug session.
The defaults are fine, but it should be on port 5005

### Preparing the Maven stuff
To run a debug session, we need the classpath configured properly.
Ensure you run a `mvn install` on `taxi-lang-server-standalone/`, which should populate
`target/dependency`

### Starting a debug session
Open the extension project from VSCode, and press F5.
This will launch a new VSCode instnace, with the extension installed, and a JS debugger attached.

To attach a Java debug session, you need to configure user settings :
 * Ctrl+Shift+P -> Open User Settings (JSON)
 * Add the following entries to the JSON:

```
"taxi.enableDebugSession" : true,
"taxi.waitForDebuggerToAttach" : false // or true, depending.
```

Note: The debug session doesn't start until you open a *.taxi file

Also, if you've configured `waitForDebuggerToAttach`, you need to launch the debug session in IntelliJ before anything will happen.
