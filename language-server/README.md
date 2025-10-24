# Taxi Language Server

[![VSCodePlugin](https://img.shields.io/badge/VSCode_Plugin-blue?style=for-the-badge&logo=visual-studio-code)](https://gitlab.com/taxi-lang/language-server/-/jobs/artifacts/master/download?job=package-plugin)

This directory contains the Taxi Language Server and editor plugins for Visual Studio Code and IntelliJ IDEA.

## IDE Support

### Visual Studio Code

The visual studio code plugin is available from Gitlab, or on the VSCode Marketplace

To install the plugin from the releases pages, use the instructions [here](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#packaging-extensions).

```bash
code --install-extension taxi-language-server-1.1.11.vsix
```

The plugin ships with a Taxi Language Server. It requires Java,
so the JRE must be installed. The plugin will try to detect where Java is running,
otherwise set the property `taxi.javaHome` via the VSCode settings.

### IntelliJ IDEA

An IntelliJ IDEA plugin is available in the `intellij-plugin/` directory.

The plugin provides full Taxi language support for IntelliJ IDEA Community and Ultimate editions, including:
- Syntax highlighting
- Code completion
- Go to definition
- Error diagnostics
- Code actions and quick fixes
- Hover information
- And more...

For installation and build instructions, see the [IntelliJ Plugin README](intellij-plugin/README.md).

## Language Server Components

- **taxi-lang-service** - Core LSP implementation
- **taxi-lang-server-standalone** - Standalone language server JAR
- **taxi-lang-server-app** - Web-based language server (websocket)
- **vscode-extension** - Visual Studio Code extension
- **intellij-plugin** - IntelliJ IDEA plugin (Gradle-based) 


