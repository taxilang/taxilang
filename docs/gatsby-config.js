module.exports = {
   pathPrefix: "/docs",
   plugins: [
      {
         resolve: "gatsby-theme-apollo-docs",
         options: {
            root: __dirname,
            siteName: "Taxi",
            pageTitle: "Taxi docs",
            menuTitle: "Taxi",
            gaTrackingId: "UA-74643563-13",
            algoliaApiKey: "2bbaa3f0c47dccb0c461c65c02943ca6",
            algoliaIndexName: "taxidocs",
            githubHost: "github.com",
            githubRepo: "taxilang/taxilang",
            baseUrl: "https://docs.taxilang.org",
            twitterHandle: "orbitalhq",
            youtubeUrl:
               "https://www.youtube.com/channel/UC0pEW_GOrMJ23l8QcrGdKSw",
            logoLink: "https://docs.taxilang.org/",
            baseDir: "docs",
            contentDir: "source",
            ffWidgetId: "3131c43c-bfb5-44e6-9a72-b4094f7ec028",
            subtitle: "Taxi",
            description: "Describing data and API contracts using Taxi",
            spectrumPath: "/",
            sidebarCategories: {
               null: [
                  "index",
                  "intro/getting-started",
                  "generating-taxi-from-source"
               ],
               Language: [
                  "language-reference/taxi-language",
                  "language-reference/types-and-models",
                  "language-reference/advanced-types",
                  "language-reference/describing-services",
                  "language-reference/functions",
                  "language-reference/taxi-and-openapi",
                  "language-reference/stdlib",
                  "language-reference/querying-with-taxiql",
                  "language-reference/taxi-projects",
               ],
               "The Taxi CLI": [
                  "taxi-cli/intro",
                  "taxi-cli/linter",
                  "taxi-cli/plugins-intro",
                  "taxi-cli/kotlin-plugin",
                  "taxi-cli/open-api-plugin",
                  "taxi-cli/writing-your-own-plugins"

               ],
               "Publishing and sharing taxonomies": [
                  "distributing-taxi/dependency-management",
                  "distributing-taxi/distributing-taxi-plugins",
               ],
               "Visual Studio Code": ["taxi-vs-code/editor-plugins"],
               Taxonomy: [
                  "best-practices-for-taxonomy-development/README",
                  "best-practices-for-taxonomy-development/building-your-base-taxonomy",
                  "best-practices-for-taxonomy-development/conventions"
               ],
               "Release notes": [
                  "release-notes/1.30",
                  "release-notes/1.31",
                  "release-notes/1.32",
                  "release-notes/1.33",
               ],
               Resources: [
                  "[Orbital](https://orbitalhq.com)",
                  "resources/faq"
               ]
            }
         }
      }
   ]
};
