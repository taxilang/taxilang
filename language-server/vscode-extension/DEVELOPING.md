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
