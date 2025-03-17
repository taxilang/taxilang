# TypeScript Projects

This directory contains TypeScript projects for Taxi Lang:

- **taxiql-client**: A TypeScript client for executing TaxiQL queries
- **taxiql-codegen**: A tool for generating TypeScript types from TaxiQL tags

## Nx Setup

The TypeScript projects in this directory are managed with [Nx](https://nx.dev/), a build system for monorepos.

### Build

```bash
# Build all TypeScript projects
npx nx run-many --target=build --parallel=3

# Build a specific project
npx nx build taxiql-client
npx nx build taxiql-codegen
```

### Test

```bash
# Test all TypeScript projects
npx nx run-many --target=test --parallel=3

# Test a specific project
npx nx test taxiql-client
npx nx test taxiql-codegen
```

### Visualize the project graph

```bash
npx nx graph
```

## Release Process

The TypeScript projects use independent versioning and can be released separately. The release process is partially automated through GitLab CI.

### Release taxiql-client

1. **Locally:**
   ```bash
   # Check out develop branch and pull latest changes
   git checkout develop
   git pull origin develop

   # Create a release branch
   git checkout -b release/client-1.2.3

   # Update version in package.json manually
   # Edit typescript/taxiql-client/package.json - update "version": "1.2.3"

   # Test the build locally
   npx nx build taxiql-client

   # Commit the version change
   git add typescript/taxiql-client/package.json
   git commit -m "Release taxiql-client v1.2.3"

   # Push the release branch
   git push origin release/client-1.2.3
   ```

2. **In GitLab:**
   - Create a merge request from the release branch to develop
   - Once approved and merged, switch back to develop locally

3. **Create the release tag:**
   ```bash
   git checkout develop
   git pull origin develop
   git tag client-v1.2.3
   git push origin client-v1.2.3
   ```

4. **In GitLab:**
   - The pipeline triggered by the tag will include a manual release job
   - Find the manual release job and click "Play" to publish to npm

### Release taxiql-codegen

1. **Locally:**
   ```bash
   # Check out develop branch and pull latest changes
   git checkout develop
   git pull origin develop

   # Create a release branch
   git checkout -b release/codegen-1.2.3

   # Update version in package.json manually
   # Edit typescript/taxiql-codegen/package.json - update "version": "1.2.3"

   # Test the build locally
   npx nx build taxiql-codegen

   # Commit the version change
   git add typescript/taxiql-codegen/package.json
   git commit -m "Release taxiql-codegen v1.2.3"

   # Push the release branch
   git push origin release/codegen-1.2.3
   ```

2. **In GitLab:**
   - Create a merge request from the release branch to develop
   - Once approved and merged, switch back to develop locally

3. **Create the release tag:**
   ```bash
   git checkout develop
   git pull origin develop
   git tag codegen-v1.2.3
   git push origin codegen-v1.2.3
   ```

4. **In GitLab:**
   - The pipeline triggered by the tag will include a manual release job
   - Find the manual release job and click "Play" to publish to npm