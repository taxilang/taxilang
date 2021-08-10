# Taxi Language Server

[![VSCodePlugin](https://img.shields.io/badge/VSCode_Plugin-blue?style=for-the-badge&logo=visual-studio-code)](https://gitlab.com/taxi-lang/language-server/-/jobs/artifacts/master/download?job=package-plugin)

The visual studio code plugin is available from Gitlab, or on the VSCode Marketplace

To install the plugin from the releases pages, use the instructions [here](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#packaging-extensions).

```bash
code --install-extension taxi-language-server-1.1.11.vsix
```

The plugin ships with a Taxi Language Server.  It requires java, 
so the JRE must be installed.  The plugin will try to detect where Java is running,
otherwise set the property `taxi.javaHome` via the VSCode settings 


