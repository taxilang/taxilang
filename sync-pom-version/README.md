# POM Version Updater

A Node.js utility that synchronizes version information between Maven's `pom.xml` and Node.js `package.json` files. Ideal for projects that use both Java and JavaScript/Node.js technologies.

## Features

- Extracts version information from `pom.xml`
- Updates `package.json` with the extracted version
- Converts Maven `-SNAPSHOT` versions to semantic versioning pre-release formats
- Command-line interface with named arguments

## Installation

Clone or download this repository, then:

```bash
# Navigate to the directory
cd sync-pom-version

# Install dependencies
npm install

# Install globally for command-line usage
npm install -g .
```

## Usage

Run the command from the directory containing both your `pom.xml` and `package.json` files:

```bash
# Basic usage - updates package.json with version from pom.xml
sync-pom-version

# Convert SNAPSHOT to beta release with build number
sync-pom-version --build 123

# Short form
sync-pom-version -b 123
```

## Version Conversion Rules

- Regular version (e.g., `1.2.3`) remains unchanged, even if a build number is provided
- `-SNAPSHOT` versions are converted to semver pre-release format only when using the `--build` option
   - Example: `1.2.3-SNAPSHOT` with build `456` becomes `1.2.3-beta.456`
   - A non-SNAPSHOT version like `1.2.3` will remain `1.2.3` even if a build number is provided

## Version Detection

The utility uses the following logic to determine the version:

1. First checks for a direct `<version>` tag in the project
2. If not found, looks for a version in the `<parent>` section
3. Errors if no version can be found in either location

This handles both standalone projects and those that inherit their version from a parent POM.

## Requirements

- Node.js 14.0.0 or higher
- A valid `pom.xml` file in the current working directory
- A valid `package.json` file in the current working directory

## License

MIT
