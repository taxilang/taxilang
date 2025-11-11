# Taxi IntelliJ IDEA Plugin

An IntelliJ IDEA plugin that provides language support for [Taxi](https://taxilang.org), a language for defining semantic API and data contracts.

## Features

This plugin provides comprehensive language support for Taxi files (`.taxi`) in IntelliJ IDEA:

- **Syntax Highlighting** - Color-coded syntax for Taxi language constructs
- **Code Completion** - Intelligent code completion for types, fields, and keywords
- **Go to Definition** - Navigate to type definitions with Ctrl+Click (or Cmd+Click on Mac)
- **Hover Information** - View type information and documentation on hover
- **Diagnostics** - Real-time error detection and reporting
- **Code Actions** - Quick fixes and refactoring actions
- **Semantic Tokens** - Advanced syntax highlighting based on semantic analysis
- **Signature Help** - Parameter hints for function calls
- **Formatting** - Code formatting support

## Architecture

This plugin uses the [LSP4IJ](https://github.com/redhat-developer/lsp4ij) framework to integrate the existing Taxi Language Server with IntelliJ IDEA. The language server is implemented using the Language Server Protocol (LSP) and is shared with the VSCode extension.

### Components

- **Language Server**: Reuses the existing `taxi-lang-server-standalone` JAR (built with Maven)
- **Plugin**: Gradle-based IntelliJ plugin that bridges IntelliJ and the language server
- **LSP4IJ**: Provides LSP client implementation for IntelliJ

## Building

### Prerequisites

- Java 17 or later
- IntelliJ IDEA 2023.2 or later (for development)
- Maven (for building the language server)
- Gradle (wrapper included)

### Build Instructions

This project uses a hybrid build system:
- The language server is built with **Maven**
- The IntelliJ plugin is built with **Gradle**

**Step 1: Build the Language Server**

First, build the Taxi language server using Maven:

```bash
cd /path/to/taxilang
./mvnw install -pl language-server/taxi-lang-server-standalone
```

This creates the language server JAR at:
```
language-server/taxi-lang-server-standalone/target/taxi-lang-server-jar-with-dependencies.jar
```

The JAR is also installed to your local Maven repository (`~/.m2/repository`).

**Step 2: Build the IntelliJ Plugin**

Navigate to the plugin directory and build with Gradle:

```bash
cd language-server/intellij-plugin
./gradlew buildPlugin
```

The plugin will be built as a `.zip` file in:
```
build/distributions/taxi-intellij-plugin-<version>.zip
```

### Verification

The build includes a verification task that checks for the language server JAR:

```bash
./gradlew verifyLanguageServer
```

If this fails, make sure you've completed Step 1 (building the language server).

## Installation

### From Build

1. Build the plugin following the instructions above
2. In IntelliJ IDEA, go to **Settings → Plugins**
3. Click the gear icon ⚙️ → **Install Plugin from Disk...**
4. Select the built plugin ZIP file from `build/distributions/`
5. Restart IntelliJ IDEA

### From JetBrains Marketplace (Coming Soon)

Once published, the plugin will be available directly from the JetBrains Plugin Marketplace.

## Development

### Running the Plugin in Development Mode

To test the plugin during development:

```bash
./gradlew runIde
```

This launches a new IntelliJ IDEA instance with the plugin installed.

### Debugging

To debug the plugin:

1. Run with debug mode:
   ```bash
   ./gradlew runIde --debug-jvm
   ```

2. In your main IntelliJ IDEA instance, create a Remote JVM Debug configuration:
   - Host: `localhost`
   - Port: `5005`

3. Start debugging with the configuration

### Project Structure

```
intellij-plugin/
├── build.gradle.kts              # Gradle build configuration
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Plugin version and properties
├── src/main/
│   ├── kotlin/org/taxilang/intellij/
│   │   ├── TaxiFileType.kt       # File type definition
│   │   ├── TaxiLanguage.kt       # Language definition
│   │   ├── TaxiIcons.kt          # Icon loader
│   │   ├── TaxiDocumentMatcher.kt    # Document matching
│   │   └── TaxiLanguageServerFactory.kt  # LSP integration
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml        # Plugin descriptor
│       └── icons/
│           └── taxi-file.svg     # File icon
└── gradle/wrapper/               # Gradle wrapper files
```

## Requirements

### Runtime Requirements

- IntelliJ IDEA 2023.2 or later (Community or Ultimate edition)
- Java 11 or later (for running the language server)

### Compatibility

- **IntelliJ IDEA**: 2023.2 - 2024.3.*
- **Platform**: IntelliJ Community Edition (IC) or Ultimate

## Configuration

The plugin works out of the box with default settings. The language server is automatically launched when you open a `.taxi` file.

### Type Checker

By default, the plugin enables the Taxi type checker. To modify this behavior, you can:

1. Modify `TaxiLanguageServerFactory.kt` to change the default type checker setting
2. The current default is `typeChecker=ENABLED`

Available options:
- `ENABLED` - Type violations reported as errors
- `DISABLED` - Type checking disabled
- `SOFT_ENABLED` - Type violations reported as warnings

## Troubleshooting

### Language Server Not Starting

If the language server fails to start:

1. Check that Java is installed and available:
   ```bash
   java -version
   ```

2. Verify the language server JAR is present in the plugin:
   - After building, the JAR should be in the plugin's distribution
   - Check IntelliJ's log files for error messages

3. Check the IntelliJ IDEA log:
   - Go to **Help → Show Log in Explorer/Finder**
   - Look for errors related to "TaxiLanguageServer"

### LSP4IJ Plugin Not Found

If you see errors about LSP4IJ during build:

1. Ensure you're using IntelliJ IDEA 2023.2 or later
2. Check your internet connection (Gradle needs to download dependencies)
3. Try clearing Gradle caches:
   ```bash
   ./gradlew clean --refresh-dependencies
   ```

### Maven Build Issues

If the language server JAR is not found:

1. Ensure you've built the language server with Maven first
2. Check that the JAR exists in `~/.m2/repository/org/taxilang/taxi-lang-server-standalone/`
3. Try rebuilding with clean:
   ```bash
   cd ../..
   ./mvnw clean install -pl language-server/taxi-lang-server-standalone
   ```

## Contributing

Contributions are welcome! Please see the main [Taxi repository](https://github.com/taxilang/taxilang) for contribution guidelines.

## License

This plugin is licensed under the Apache License 2.0. See the LICENSE file in the parent directory for details.

## Links

- [Taxi Language](https://taxilang.org)
- [Taxi GitHub Repository](https://github.com/taxilang/taxilang)
- [LSP4IJ](https://github.com/redhat-developer/lsp4ij)
- [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
