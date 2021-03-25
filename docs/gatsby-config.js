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
            githubHost: "gitlab.com",
            githubRepo: "taxi-lang/taxi-lang",
            baseUrl: "https://docs.taxilang.org",
            twitterHandle: "apollographql",
            spectrumHandle: "vyne-dec",
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
                  "intro/benefits",
                  "intro/platform",
                  "generating-taxi-from-source"
               ],
               Language: [
                  "language-reference/taxi-language",
                  "language-reference/projects-and-names",
                  "language-reference/types-and-models",
                  "language-reference/advanced-types",
                  "language-reference/describing-services"
               ],
               "The Taxi CLI": [
                  "taxi-cli/intro",
                  "taxi-cli/taxi-conf-file",
                  "taxi-cli/linter",
               ],
               "Publishing and sharing taxonomies": [
                  "distributing-taxi/distributing-taxi-projects",
                  "distributing-taxi/distributing-taxi-plugins",
                  "distributing-taxi/taxi-hub",
               ],
               Plugins: [
                  "plugins/README",
                  "plugins/kotlin-plugin",
                  "plugins/writing-your-own-plugins"
               ],
               "Visual Studio Code": ["taxi-vs-code/editor-plugins"],
               Testing: [
                  "testing/running-taxi-in-a-ci-cd-pipeline",
                  "testing/testing-taxi"
               ],
               Taxonomy: [
                  "best-practices-for-taxonomy-development/README",
                  "best-practices-for-taxonomy-development/building-your-base-taxonomy",
                  "best-practices-for-taxonomy-development/conventions"
               ],
               Resources: [
                  "[Vyne](https://vyne.co)",
                  "[Princpled Data](https://vyne.co)",
                  "resources/faq"
               ]
            }
         }
      }
   ]
};