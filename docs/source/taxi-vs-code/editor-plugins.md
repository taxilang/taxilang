---
title: Taxi extension for VS Code
description: Build Taxi projects rapidly within Visual Studio Code
---

Our [Visual Studio Code plugin](https://marketplace.visualstudio.com/items?itemName=taxi-lang.taxi-language-server) 
provides a rich editor experience when building taxi projects.

 * As you type compilation feedback
 * Automatic imports
 * Download of dependencies
 * Linting
 * Code formatting
 * Click to navigate
 * Documentation on hover

... plus all the other goodness you know & love from VSCode.

Install from the link above, or by pasting the below in the VS Code Quick Open panel (Ctrl+P):

```bash
ext install taxi-lang.taxi-language-server
```

### Language Service
In addition to the VSCode plugin, we also have a general purpose [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
implementation available, which can be used for building your own advanced taxi language tooling.

The code for the LSP server, and the VSCode plugin is available under the Apache 2.0 License, available [here](https://gitlab.com/taxi-lang/language-server).
