# Overview

## Welcome to Taxi

![](https://img.shields.io/bintray/v/taxi-lang/releases/parent?label=Taxi&style=for-the-badge)

Taxi is a language for documenting APIs - the models they return, and the operations they can perform.

As a language, it focuses on:

* **Readability**  - A familiar syntax that should be easy to write, and easy to understand.
* **Typesafe** - A strongly typed, expressive language, purpose-built for describing API operations & types
* **Tooling** - Taxi is intended to allow next-generation tooling integration - the syntax allows rich expression of what services can do \(rather than just where to find them\).
* **Extensibility** - Taxi allows you to refine and compose API schemas, adding context, annotations, and improving type signatures. 

Taxi is used heavily to power [Vyne](https://vyne.co) - and the projects have influenced each other & evolved together.    The expressiveness of Taxi allows Vyne to automate integration between services.

However, Taxi is intended to be a standalone tool, not coupled to Vyne.

### Getting Started

To get started you need to download and install Taxi. Download the [taxi.zip](https://gitlab.com/taxi-lang/taxi-lang/-/jobs/artifacts/master/raw/taxi-cli/target/taxi-cli.zip?job=publish-release) file and extract it.

Once the file is downloaded and extracted, you need to add it to your path. It will look something like this:

```text
/install-location/taxi/bin
```

Once the route to the bin file has been added, open a command line/terminal window and run the command below:

`taxi`

This will install taxi onto your machine and make it available to use.

Create your first Taxi file, named `person.taxi`:

{% code title="person.taxi" %}
```text
namespace demo {
   type Person {
      id : PersonId as String
      firstName : FirstName as String
      lastName : LastName as String
   }
}
```
{% endcode %}

And create a project file, named `taxi.conf`:

{% code title="taxi.conf" %}
```text
name: demo/hello-world
version: 0.1.0
plugins {
   taxi/kotlin {}
}
```
{% endcode %}

Then compile:

```text
taxi build
```

And you should have some fresh Kotlin files generated, to match your Person object.

This has been a fairly simple example.  Take a look at some of the language features to get a better understanding of how Taxi can help you deliver better API documentation.

### Swagger & RAML

Taxi draws heavy inspiration from Swagger & RAML, and was born from having spent years working within the Swagger ecosystem, being equally inspired by the project, and frustrated by some of it's limitations.

Taxi aims to be fully compatible with these tools, and exist as a superset of their features.  There is a generator for converting [Swagger to Taxi](https://gitlab.com/taxi-lang/taxi-lang/tree/master/swagger2taxi), and [Taxi to Swagger](https://gitlab.com/taxi-lang/taxi-lang/issues/10) is on the roadmap.

However, Swagger and RAML leverage general-purpose syntaxes \(such as JSON and YAML\), Taxi has it's own bespoke language, resulting in much clearer to read API definitions.

While Taxi has a bright future, and there are big plans, it's definitely the new kid on the block - and currently doesn't have feature parity with it's more mature counterparts.  However, we intend to address that on the road to 1.0.

