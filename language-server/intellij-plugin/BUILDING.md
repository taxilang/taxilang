# Building the Taxi IntelliJ Plugin

## Quick Start

```bash
# 1. Build the language server (Maven)
cd /path/to/taxilang
./mvnw install -pl language-server/taxi-lang-server-standalone

# 2. Build the plugin (Gradle)
cd language-server/intellij-plugin
./gradlew buildPlugin

# 3. Install in IntelliJ
# The plugin ZIP will be at: build/distributions/taxi-intellij-plugin-<version>.zip
# Install via Settings → Plugins → Install from Disk
```

## Detailed Build Process

### Prerequisites

Ensure you have the following installed:

- **Java 17+** - Required for building both Maven and Gradle projects
- **Internet connection** - For downloading dependencies

Verify Java installation:
```bash
java -version
```

### Step 1: Build the Language Server

The IntelliJ plugin depends on the language server JAR. Build it first:

```bash
cd /path/to/taxilang
./mvnw clean install -pl language-server/taxi-lang-server-standalone
```

**What this does:**
- Compiles the Taxi language server
- Creates a fat JAR: `taxi-lang-server-jar-with-dependencies.jar`
- Installs to local Maven repo: `~/.m2/repository/org/taxilang/`

**Troubleshooting:**
- If `./mvnw` fails, try using system Maven: `mvn clean install -pl language-server/taxi-lang-server-standalone`
- If you get network errors, check your internet connection
- If you get compilation errors, ensure you're using Java 17+

### Step 2: Build the IntelliJ Plugin

Navigate to the plugin directory:

```bash
cd language-server/intellij-plugin
```

Build the plugin:

```bash
./gradlew buildPlugin
```

**What this does:**
- Downloads Gradle dependencies (first time only)
- Compiles Kotlin source files
- Packages the plugin with the language server JAR
- Creates a ZIP file in `build/distributions/`

**Available Gradle Tasks:**

- `./gradlew buildPlugin` - Build the plugin ZIP
- `./gradlew verifyLanguageServer` - Check if language server JAR is available
- `./gradlew runIde` - Launch IntelliJ with the plugin installed (for testing)
- `./gradlew clean` - Clean build artifacts
- `./gradlew test` - Run tests (when added)

**Troubleshooting:**
- If you see "Taxi Language Server JAR not found", ensure Step 1 completed successfully
- If Gradle fails to download dependencies, check your internet connection
- If you get "Could not determine Java version", ensure JAVA_HOME is set correctly

### Step 3: Install the Plugin

Once built, the plugin ZIP will be at:
```
build/distributions/taxi-intellij-plugin-1.66.0-SNAPSHOT.zip
```

**Installation in IntelliJ IDEA:**

1. Open IntelliJ IDEA
2. Go to **File → Settings** (or **IntelliJ IDEA → Preferences** on Mac)
3. Navigate to **Plugins**
4. Click the gear icon ⚙️ → **Install Plugin from Disk...**
5. Select the ZIP file from `build/distributions/`
6. Click **OK**
7. Restart IntelliJ IDEA when prompted

### Step 4: Verify Installation

After restarting IntelliJ:

1. Create or open a `.taxi` file
2. You should see:
   - Syntax highlighting
   - The Taxi icon in the file tab
   - Code completion when typing
   - Error highlighting for invalid syntax

## Development Workflow

### Testing Changes

When making changes to the plugin code:

```bash
# Quick iteration without full build
./gradlew runIde
```

This launches a sandbox IntelliJ instance with your plugin installed.

### Debugging the Plugin

To debug the plugin code:

```bash
./gradlew runIde --debug-jvm
```

Then in your development IntelliJ:
1. Create a "Remote JVM Debug" run configuration
2. Set Host: `localhost`, Port: `5005`
3. Start debugging

### Debugging the Language Server

To debug the language server itself, modify `TaxiLanguageServerFactory.kt` to add Java debug arguments:

```kotlin
val commands = mutableListOf(
    javaExecutable,
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006",
    "-jar",
    serverJar,
    "typeChecker=ENABLED"
)
```

Then attach a debugger to port 5006.

### Making Changes to the Language Server

If you modify the language server code:

1. Rebuild the language server:
   ```bash
   cd /path/to/taxilang
   ./mvnw install -pl language-server/taxi-lang-service,language-server/taxi-lang-server-standalone
   ```

2. Rebuild the plugin:
   ```bash
   cd language-server/intellij-plugin
   ./gradlew clean buildPlugin
   ```

3. Reinstall the plugin in IntelliJ

## CI/CD Integration

### Automated Builds

For CI/CD pipelines, use this sequence:

```bash
# Build everything
./mvnw install -pl language-server/taxi-lang-server-standalone -DskipTests
cd language-server/intellij-plugin
./gradlew buildPlugin

# The artifact will be at:
# language-server/intellij-plugin/build/distributions/taxi-intellij-plugin-*.zip
```

### Publishing to JetBrains Marketplace

To publish the plugin (requires credentials):

```bash
./gradlew publishPlugin -Ppublish.token=<YOUR_TOKEN>
```

See [JetBrains Plugin Repository documentation](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html) for details.

## Common Issues

### Issue: "Could not find taxi-lang-server-standalone"

**Solution:** Build the language server first:
```bash
cd /path/to/taxilang
./mvnw install -pl language-server/taxi-lang-server-standalone
```

### Issue: "Unsupported class file major version"

**Solution:** Ensure you're using Java 17 or later:
```bash
java -version
```

If you have multiple Java versions, set JAVA_HOME:
```bash
export JAVA_HOME=/path/to/java17
```

### Issue: Gradle daemon issues

**Solution:** Stop all Gradle daemons and retry:
```bash
./gradlew --stop
./gradlew clean buildPlugin
```

### Issue: Plugin not loading in IntelliJ

**Solution:**
1. Check IntelliJ version (must be 2023.2+)
2. Check the log: **Help → Show Log in Explorer/Finder**
3. Look for errors related to "TaxiLanguageServer" or "lsp4ij"

### Issue: LSP4IJ not found

**Solution:**
1. Ensure you have internet access (Gradle needs to download LSP4IJ)
2. Try refreshing dependencies:
   ```bash
   ./gradlew clean build --refresh-dependencies
   ```

## Project Structure

```
intellij-plugin/
├── build.gradle.kts          # Main build file
├── settings.gradle.kts       # Project settings
├── gradle.properties         # Build properties
├── gradlew                   # Gradle wrapper (Unix)
├── gradlew.bat              # Gradle wrapper (Windows)
├── gradle/wrapper/          # Wrapper files
├── src/main/
│   ├── kotlin/              # Kotlin source files
│   └── resources/           # Resources (icons, plugin.xml)
└── build/                   # Build output (generated)
    ├── libs/                # Compiled JARs
    └── distributions/       # Plugin ZIP files
```

## Additional Resources

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [LSP4IJ Documentation](https://github.com/redhat-developer/lsp4ij)
- [Gradle IntelliJ Plugin](https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html)
- [Taxi Language Documentation](https://docs.taxilang.org)
