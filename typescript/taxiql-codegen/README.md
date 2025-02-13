# @orbitalhq/taxiql-codegen

A tool for generating types from `taxiql` tags and for typing the responses of queries executed using the `@orbitalhq/orbital-client` SDK.

## Installation

You can install `@orbitalhq/taxiql-codegen` via npm:

```sh
npm install @orbitalhq/taxiql-codegen --save-dev
```

## Usage

### Running the Code Generator

Once installed, you can run the code generator using:

```sh
npx taxiql-codegen
```

By default, the tool looks for a configuration file named `codegen.config.ts` in the root of your project.

### Specifying a Custom Configuration File

If you need to provide a custom configuration file, you can specify it using the `--config` flag:

```sh
npx taxiql-codegen --config path/to/custom-config.ts
```

## Scripts

### Building the Project

```sh
npm run build
```

This bundles the TypeScript source files using `esbuild` and outputs the result to `dist/build.js`.

### Running Test Queries

```sh
npm run run:test-queries
```

This compiles and executes the test queries.

### Running Tests

```sh
npm test
```

This runs unit tests using `vitest`.

### Publishing

Before publishing the package, the `prepublishOnly` script ensures the project is built:

```sh
npm run prepublishOnly
```

## Dependencies

### Peer Dependencies

- `@orbitalhq/taxiql-client`: The client library required for executing TaxiQL queries.

### Optional Dependencies

> [!WARNING]
> Running npm install without optional depdency install (`--no-optional`) will mean the binaries required to generate the codegen will be missing.

Platform-specific CLI binaries:

- `@orbitalhq/taxiql-codegen-cli-darwin` (macOS)
- `@orbitalhq/taxiql-codegen-cli-linux` (Linux)
- `@orbitalhq/taxiql-codegen-cli-win32` (Windows)

## TODO

- [ ] Ensure commented out `taxiQl` tags aren't extracted for type generation

## License

This project is licensed under the Apache-2.0 License.

