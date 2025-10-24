# Taxi IntelliJ Plugin - Implementation Summary

## Overview

This document summarizes the implementation of the Taxi IntelliJ IDEA plugin, which provides full language support for Taxi files (.taxi) using the Language Server Protocol (LSP).

## Architecture

### Design Approach

The plugin follows a **minimal wrapper** design pattern:

1. **Reuses existing infrastructure**: The plugin reuses the existing `taxi-lang-server-standalone` JAR, which is already proven and working in VSCode
2. **LSP4IJ integration**: Uses the LSP4IJ framework to bridge IntelliJ with the language server
3. **Hybrid build system**: Keeps Maven for the language server, adds Gradle only for the IntelliJ plugin
4. **Zero duplication**: No LSP implementation code is duplicated - the plugin is purely a thin client

### Components

```
┌─────────────────────────────────────────────────────┐
│           IntelliJ IDEA                             │
├─────────────────────────────────────────────────────┤
│  Taxi Plugin (Gradle)                               │
│  ┌───────────────────────────────────────────────┐  │
│  │ TaxiLanguageServerFactory                     │  │
│  │   ├─ Launches language server process         │  │
│  │   ├─ Manages stdin/stdout communication       │  │
│  │   └─ Finds Java & server JAR                  │  │
│  │                                                │  │
│  │ TaxiFileType                                   │  │
│  │   └─ Registers .taxi file extension           │  │
│  │                                                │  │
│  │ TaxiDocumentMatcher                            │  │
│  │   └─ Routes .taxi files to language server    │  │
│  └───────────────────────────────────────────────┘  │
│                       ↕                             │
│              LSP4IJ Framework                        │
│  (Handles LSP protocol, features, UI integration)   │
└─────────────────────────────────────────────────────┘
                        ↕ LSP Protocol
┌─────────────────────────────────────────────────────┐
│  Taxi Language Server (Maven)                       │
│  ┌───────────────────────────────────────────────┐  │
│  │ taxi-lang-server-standalone.jar               │  │
│  │   ├─ LSP4J server implementation              │  │
│  │   ├─ Taxi compiler integration                │  │
│  │   ├─ Completion, hover, goto definition       │  │
│  │   ├─ Diagnostics & code actions               │  │
│  │   └─ Semantic tokens, formatting              │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

## Implementation Details

### File Structure

```
intellij-plugin/
├── build.gradle.kts                    # Gradle build configuration
├── settings.gradle.kts                 # Gradle settings
├── gradle.properties                   # Plugin metadata and versions
├── gradlew, gradlew.bat               # Gradle wrapper scripts
├── gradle/wrapper/                     # Gradle wrapper files
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
│
├── README.md                          # User documentation
├── BUILDING.md                        # Build instructions
├── IMPLEMENTATION_SUMMARY.md          # This file
├── .gitignore                         # Git ignore rules
│
└── src/main/
    ├── kotlin/org/taxilang/intellij/
    │   ├── TaxiLanguage.kt           # Language definition (15 lines)
    │   ├── TaxiFileType.kt           # File type registration (24 lines)
    │   ├── TaxiIcons.kt              # Icon loader (25 lines)
    │   ├── TaxiDocumentMatcher.kt    # Document matching (11 lines)
    │   └── TaxiLanguageServerFactory.kt  # LSP integration (170 lines)
    │
    └── resources/
        ├── META-INF/
        │   └── plugin.xml            # Plugin descriptor
        └── icons/
            └── taxi-file.svg         # File icon

Total Kotlin code: ~245 lines
```

### Key Classes

#### 1. TaxiLanguage.kt

Defines the Taxi language for IntelliJ:

```kotlin
object TaxiLanguage : Language("Taxi") {
    override fun getDisplayName(): String = "Taxi"
    override fun isCaseSensitive(): Boolean = true
}
```

**Purpose**: Registers "Taxi" as a language in IntelliJ's language registry.

#### 2. TaxiFileType.kt

Registers the .taxi file extension:

```kotlin
class TaxiFileType private constructor() : LanguageFileType(TaxiLanguage) {
    override fun getName(): String = "Taxi"
    override fun getDescription(): String = "Taxi language file"
    override fun getDefaultExtension(): String = "taxi"
    override fun getIcon(): Icon? = TaxiIcons.FILE

    companion object {
        @JvmStatic
        val INSTANCE = TaxiFileType()
    }
}
```

**Purpose**: Associates .taxi files with the Taxi language and provides file metadata.

#### 3. TaxiIcons.kt

Loads the file icon:

```kotlin
object TaxiIcons {
    @JvmStatic
    val FILE: Icon = loadIcon("/icons/taxi-file.svg")

    private fun loadIcon(path: String): Icon {
        return try {
            IconLoader.getIcon(path, TaxiIcons::class.java)
        } catch (e: Exception) {
            IconLoader.getIcon("/fileTypes/text.svg", TaxiIcons::class.java)
        }
    }
}
```

**Purpose**: Provides the icon shown in file tabs and project trees. Falls back to default if icon is missing.

#### 4. TaxiDocumentMatcher.kt

Determines which files should use the language server:

```kotlin
class TaxiDocumentMatcher : DocumentMatcher {
    override fun match(file: VirtualFile, project: Project): Boolean {
        return file.extension == "taxi" || file.name == "taxi.conf"
    }
}
```

**Purpose**: Routes .taxi and taxi.conf files to the Taxi language server.

#### 5. TaxiLanguageServerFactory.kt

The main integration class that:

1. **Finds the language server JAR**: Searches the plugin's classpath for `taxi-lang-server-standalone.jar`
2. **Finds Java**: Locates Java executable (JAVA_HOME or system PATH)
3. **Launches the server**: Starts the JAR as a subprocess with `java -jar`
4. **Manages communication**: Connects stdin/stdout to LSP4IJ
5. **Handles lifecycle**: Starts and stops the server process

**Key methods**:
- `start()`: Launches the language server process
- `stop()`: Gracefully shuts down the server
- `findJavaExecutable()`: Locates Java runtime
- `findLanguageServerJar()`: Locates the server JAR in the plugin

### Plugin Descriptor (plugin.xml)

Defines:
- Plugin metadata (name, version, description)
- Dependencies: `com.redhat.devtools.lsp4ij`
- File type registration
- Language server configuration
- Language mapping (Taxi → taxiLanguageServer)

### Build Configuration

#### build.gradle.kts

Key features:
- **Kotlin JVM plugin**: For compiling Kotlin code
- **IntelliJ Platform plugin**: For building IntelliJ plugins
- **LSP4IJ dependency**: Version 0.6.0 from JetBrains Marketplace
- **Language server dependency**: References Maven artifact with LSP4J excluded
- **Verification task**: Checks that language server JAR is available
- **Target platform**: IntelliJ Community 2023.2+

#### gradle.properties

Defines:
- Plugin version: Synced with parent POM (1.66.0-SNAPSHOT)
- IntelliJ compatibility: 2023.2 to 2024.3.*
- Build configuration

## Build Process (Method A)

### Step 1: Build Language Server (Maven)

```bash
cd /path/to/taxilang
./mvnw install -pl language-server/taxi-lang-server-standalone
```

**Output**:
- JAR: `language-server/taxi-lang-server-standalone/target/taxi-lang-server-jar-with-dependencies.jar`
- Installed to: `~/.m2/repository/org/taxilang/taxi-lang-server-standalone/1.66.0-SNAPSHOT/`

### Step 2: Build Plugin (Gradle)

```bash
cd language-server/intellij-plugin
./gradlew buildPlugin
```

**Process**:
1. Gradle resolves dependencies from Maven Local (`~/.m2`)
2. Finds `taxi-lang-server-standalone-1.66.0-SNAPSHOT.jar`
3. Compiles Kotlin sources
4. Packages plugin with the language server JAR
5. Creates ZIP: `build/distributions/taxi-intellij-plugin-1.66.0-SNAPSHOT.zip`

**Verification**: The `verifyLanguageServer` task ensures the JAR is available before building.

### Step 3: Installation

Users install the plugin ZIP via IntelliJ's "Install Plugin from Disk" feature.

## Features Provided

All features are provided by the language server via LSP:

✅ **Syntax Highlighting** - Via semantic tokens
✅ **Code Completion** - Types, fields, keywords
✅ **Go to Definition** - Navigate to type definitions
✅ **Hover Information** - Type info and documentation
✅ **Diagnostics** - Real-time error detection
✅ **Code Actions** - Quick fixes (e.g., remove unused imports)
✅ **Formatting** - Code formatting
✅ **Signature Help** - Parameter hints

## Dependencies

### Runtime Dependencies

- **IntelliJ Platform**: 2023.2+ (Community or Ultimate)
- **LSP4IJ**: 0.6.0 (bundled with plugin)
- **Java**: 11+ (for running the language server)
- **Taxi Language Server JAR**: Bundled in plugin

### Build Dependencies

- **Kotlin**: 1.9.24
- **Gradle**: 8.5 (via wrapper)
- **IntelliJ Platform Gradle Plugin**: 1.17.4
- **LSP4IJ**: 0.6.0 (from JetBrains Marketplace)

## Testing Strategy

### Manual Testing

1. **Run in sandbox**: `./gradlew runIde`
2. **Open .taxi files**: Verify syntax highlighting, completion, errors
3. **Test navigation**: Go to definition, hover, etc.

### Automated Testing (Future)

To add automated tests:

1. Create `src/test/kotlin/` directory
2. Add test classes extending `BasePlatformTestCase`
3. Write tests for file type recognition, document matching
4. Run with: `./gradlew test`

## Deployment

### Local Installation

Users can build and install locally following BUILDING.md.

### JetBrains Marketplace (Future)

To publish:

1. Create JetBrains account
2. Generate plugin repository token
3. Run: `./gradlew publishPlugin -Ppublish.token=<TOKEN>`

## Design Decisions

### Why LSP4IJ?

**Pros**:
- Works with IntelliJ Community (free)
- Mature, actively developed by Red Hat
- Excellent documentation and examples
- Handles all LSP protocol details

**Alternative (Native JetBrains LSP API)**:
- Only works with IntelliJ Ultimate (paid)
- Less suitable for open-source projects

**Decision**: Use LSP4IJ for maximum accessibility.

### Why Method A (Pre-build requirement)?

**Pros**:
- Simple and clear
- Separation of concerns (Maven for server, Gradle for plugin)
- Easy to understand and maintain
- No complex build orchestration

**Alternatives**:
- Method B: Gradle executes Maven (more complex)
- Method C: Composite build (most complex)

**Decision**: Simplicity wins. Method A is clear and maintainable.

### Why Hybrid Maven/Gradle?

**Pros**:
- IntelliJ plugins require Gradle
- Existing Maven infrastructure for language server
- No need to migrate entire monorepo
- Each tool used for what it does best

**Alternative**:
- Migrate everything to Gradle (massive refactoring)

**Decision**: Hybrid approach minimizes disruption.

### Why Bundle the JAR?

**Alternatives**:
1. Bundle JAR in plugin (chosen)
2. Download JAR on first use
3. Require separate installation

**Decision**: Bundle for simplicity and offline support.

## Maintenance

### Updating Dependencies

**LSP4IJ updates**:
```kotlin
plugins.set(listOf("com.redhat.devtools.lsp4ij:0.X.Y"))
```

**IntelliJ Platform version**:
```properties
platformVersion = 202X.Y
```

### Version Synchronization

Plugin version is defined in `gradle.properties`:
```properties
pluginVersion = 1.66.0-SNAPSHOT
```

This should be kept in sync with the parent POM version.

### Language Server Updates

When the language server is updated:
1. Rebuild the language server JAR
2. Rebuild the plugin
3. Test all features
4. Release new plugin version

## Known Limitations

1. **Network required for initial build**: Gradle and Maven need internet for dependency download
2. **Build order dependency**: Must build language server before plugin
3. **No bundled JRE**: Requires Java to be installed on user's system
4. **Icon is basic**: Could be improved with better branding

## Future Enhancements

### Short Term
- [ ] Add automated tests
- [ ] Improve icon design
- [ ] Add language configuration (brackets, comments)
- [ ] Add plugin settings UI

### Medium Term
- [ ] Publish to JetBrains Marketplace
- [ ] Add CI/CD pipeline
- [ ] Bundle Java runtime (JBR)
- [ ] Add telemetry/usage tracking

### Long Term
- [ ] Custom IntelliJ features (beyond LSP)
- [ ] Integration with IntelliJ build tools
- [ ] Taxi project templates
- [ ] Integration with other JetBrains IDEs (WebStorm, etc.)

## Resources

- **Plugin Code**: `language-server/intellij-plugin/`
- **Language Server**: `language-server/taxi-lang-service/`
- **Build Docs**: `BUILDING.md`
- **User Docs**: `README.md`

## Success Metrics

The implementation is considered successful if:

✅ Plugin builds without errors
✅ Plugin installs in IntelliJ IDEA
✅ .taxi files are recognized and highlighted
✅ LSP features work (completion, errors, etc.)
✅ No code duplication from existing language server
✅ Build process is documented and reproducible
✅ Compatible with IntelliJ Community Edition

## Conclusion

The Taxi IntelliJ plugin successfully wraps the existing language server with minimal code (~245 lines of Kotlin). It uses industry-standard tools (LSP4IJ, Gradle) and follows best practices for IntelliJ plugin development. The hybrid Maven/Gradle build system keeps the implementation simple while enabling Gradle-based plugin development.
