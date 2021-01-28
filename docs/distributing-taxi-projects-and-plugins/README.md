# Distributing Taxi projects and plugins

Once built, Taxi project and plugins can be packaged and published to a repository to allow other projects to depend on them.

This is an advanced feature in its infancy.  We will provide further doucmentation as the implementation stabilizes.

For now, the code serves as the best documentation.

See:

| Code area | Purpose |
| :--- | :--- |
| [Package Importer](https://gitlab.com/taxi-lang/taxi-lang/-/tree/develop/package-importer) | Responsible for downloading dependencies |
| [Package Repository API](https://gitlab.com/taxi-lang/taxi-lang/-/tree/develop/package-repository-api) | The public configuration API for managing dependencies on other packages |
| [Publish Plugin Command](https://gitlab.com/taxi-lang/taxi-lang/-/tree/develop/package-repository-api) | Packages and uploads a custom plugin to an external repository |
|  |  |

