# Taxi

Welcome! 👋 Taxi is a language for documenting data - such as data models - and the contracts of APIs.

Specifically, Taxi aims to describe how data & APIs across an ecosystem relate with one another, and to allow consumers
to
compose services together, without being coupled to underlying APIs.

Taxi describes data *semantically* - using rich semantic types - ie., types that, which allows powerful tooling to
discover and map data based on it's meaning, rather than the name of a field.

![](https://img.shields.io/badge/dynamic/xml.svg?label=Taxi&url=http%3A%2F%2Frepo.orbitalhq.com%2Frelease%2Forg%2Ftaxilang%2Fparent%2Fmaven-metadata.xml&query=%2F%2Frelease&colorB=green&prefix=v&style=for-the-badge&logo=kotlin&logoColor=white)

## Quick links

* [Docs](https://taxilang.org)
* [Installing the CLI](https://taxilang.org/taxi-cli/intro/#install)
   * [Using a pre-release version of the cli](#trying-a-pre-release-version)
* [Developer Notes](#developer-notes)
   * [Releasing](#releasing)
   * [Maven co-ordinates](#maven-co-ordinates)

## Example

Taxi allows you to be really expressive about what the meaning is of data returned from an API. By describing the *
*meaning**  of our data, we can start to make data interchangeable.

For example:

```
model Customer {
   id : CustomerId inherits String
   firstName : FirstName inherits String
   lastName : LastName inherits String
}

model Invoice {
   invoiceId : InvoiceId inherits Int
   customerId : CustomerId
}
```

Those `String`s now have a little more meaning. And look - we now know that `Invoice.customerId` and `Customer.id`
describe the same piece of information - a `CustomerId`.

By building out this set of terms - we create a Taxonomy of interchangeable types. This allows us to better document how
data is intended to be used. Tooling such as [Orbital](https://orbitalhq.com)
can leverage this to link and discover data automatically, and automate API integration.

```
@HttpOperation(method = "GET", url = "http://myApp/customers/{id}")
operation getPerson(id: CustomerId):Customer
```

## Getting started

### Playground - Voyager

[Voyager](https://voyager.orbitalhq.com) is a playground for quickly writing Taxi, and generating diagrams. Read more about
why we built Voyager, and where it's going, [here](https://blog.vyne.co/introducing-voyager/)

## FAQ's

### Why do we need another schema language?

There's a much lengthier blog discussing why we built
Taxi [here](https://orbitalhq.com/blog/2023-05-12-why-we-created-taxi)

There's also an answer to this question in our [docs](https://docs.taxilang.org/#why-do-we-need-another-schema-language)

### How does Taxi relate to OpenAPI / RAML / AsyncApi etc.

Taxi has [strong interoperability with OpenApi](/language-reference/taxi-and-openapi/). While you can use
Taxi to fully describe REST-ish APIs in the same way you can use OpenAPI, most people prefer to combine the two.

It's more common to embed Taxi metadata inside your existing OpenAPI specs, to get the best of both worlds. This lets
you use
OpenAPI to describe the capabilities of an API, and Taxi metadata describes how the inputs and data returned relate to
other systems
in your ecosystem.

In comparing Taxi and OpenAPI, there's some areas where Taxi is stronger:

* Readability & Writability - you can easily sketch a Taxi spec by hand, because it's a dedicated DSL, rather than YAML
* Taxi's type system is much richer and more expressive. It has a full semantic type system, and compiler to validate
  that API contracts are correct.
* Taxi is better at describing how data relates **between** APIs - OpenAPI's goal is to describe a single API, not the
  relationship between multiple APIs
* Taxi's documentation goals are broader than just HTTP APIs, it includes Message queues, Serverless functions,
  Databases, etc.

But, in most other areas other OpenAPI is stronger:

* It's the defacto standard - pretty much everyone understands what OpenAPI is.
* The tooling ecosystem is awesome.
* It's more mature, having had the benefit of thousands of developers collaborating for years.

### Taxi vs Protobuf / Avro

In addition to the differences with OpenAPI / RAML, Protobuf and Avro are also an encoding specification - they
document how payloads are serialized to bytes.

Taxi does not attempt to be a serialization protocol.

### Taxi vs GraphQL

Taxi (and espeically the query language - TaxiQL) and GraphQL share similar goals - providing a single entrypoint for
composing multiple APIs together.

However, there are subtle, but key differences in their approach:

#### TaxiQL doesn't need resolvers

One of the goals of Taxi is to allow software to automate integration between systems, without engineers having
to write glue code. It's semantic types become the links between data sources.

In comparison, GraphQL relies on resolvers to code how to stitch together APIs. These resolvers need to be maintained,
such that breaking changes in upstream systems need to be propagated into resolver code.

#### TaxiQL is protocol agnostic

GraphQL federation requires "GraphQL everywhere". Taxi aims to work with the API specs you already have, and can bridge
between OpenAPI, RAML, JsonSchema and Protobuf without requiring exisitng schemas to be replaced.

Taxi can also bridge between Databases, but - like GraphQL - that *does* require a Taxi schema in addition to the
DDL.  (We haven't figured
out a neat way to embed taxi metadata in DDL scripts. If you have ideas, we'd love to hear them).

#### TaxiQL allows consumers to define their data contracts

GraphQL has a single schema, that consumers can cherry-pick fields from. This single schema can become difficult to
refactor it,
as all the consumers also need to change.

TaxiQL is designed to allow consumers to define the contract of data they want, and it's up to the query engine to
satisfy this
contract. This means that as publisher contracts change, consumers remain decoupled, keeping cost-of-change low.

## Language Goals

As a language, Taxi focuses on:

* **Readability**  - A familiar syntax that should be easy to write, and easy to understand.
* **Expressiveness** - Taxi should be able to describe the semantic meaning of your data, and rich quirky contracts of
  our APIs
* **Typesafe** - A strongly typed, expressive language, purpose-built for describing API operations & types
* **Tooling** - Taxi is intended to allow next-generation tooling integration - the syntax allows rich expression of
  what services can do \(rather than just where to find them\).
* **Extensibility** - Taxi allows you to refine and compose API schemas, adding context, annotations, and improving type
  signatures.

Taxi is used heavily to power [Orbital](https://orbitalhq.com) - and the projects have influenced each other & evolved
together. The expressiveness of Taxi allows Orbital to automate integration between services.

However, Taxi is intended to be a standalone tool, and is not coupled to Orbital. There's lots of amazing things you can
do with Taxi on it's own.

## New to Taxi or Orbital?

It's easy to adopt Taxi incrementally, meaning you can set it up alongside an existing solution (such as Swagger or XML
schemas) and migrate functionality at your convenience.

In fact, Taxi is designed to complement those tools, and you can happily use Swagger or XSDs are you base schema, and
then overlay semantic data using Taxi.

## Developer notes

### Maven Co-ordinates

Currently, we publish to the Orbital maven repo:

```xml

<repositories>
   <repository>
      <id>orbital-snapshots</id>
      <url>https://repo.orbitalhq.com/snapshot</url>
      <snapshots>
         <enabled>true</enabled>
      </snapshots>
   </repository>

   <repository>
      <id>orbital</id>
      <url>https://repo.orbitalhq.com/release</url>
      <snapshots>
         <enabled>false</enabled>
      </snapshots>
   </repository>
</repositories>
```

Generally, tooling will want to make use of the compiler, which is:

```xml

<dependency>
   <groupId>org.taxilang</groupId>
   <artifactId>compiler</artifactId>
   <version>${latest-version}</version>
</dependency>
```

Other artifacts are available using their appropriate modules artifactId.

We plan on publishing to maven central too. Track / Vote for [this issue](https://github.com/taxilang/taxilang/issues/3)
for updates.

### Releasing

To publish a release to the oribtal repo, you must first export your AWS credentials with appropriate access:

```bash
export AWS_ACCESS_KEY_ID=xxxx
export AWS_SECRET_ACCESS_KEY=xxxx
```

And, in your `~/.m2/settings.xml`, have the following:

```xml
<settings>
    <profiles>
        <profile>
            <id>sdkman</id>
            <properties>
                <sdkman.consumer.key>xxx</sdkman.consumer.key>
                <sdkman.consumer.token>xxx</sdkman.consumer.token>
            </properties>
        </profile>
    </profiles>
</settings>
```

```
# Can use mvn too, but it's slower.
mvnd clean install
# deploy doesn't work great with mvnd
# 
mvn deploy -P orbital-repo,sdkman
```

This will publish all the jars, as well as the `taxi-cli` zip to the Orbital repo, and release on sdkman.

### Trying a pre-release version

The easiest way to use a prerelease version is with the `get-taxi-preview.sh` script in this repo.

It will download the latest snapshot of a given version. eg:

```bash
wget https://raw.githubusercontent.com/taxilang/taxilang/develop/get-taxi-preview.sh
chmod +x get-taxi-preview.sh
./get-taxi-preview 1.37.0       
```

The above example will download the latest prerelease of version `1.37.0`.

It will be downloaded to `.taxi/1.37.0` relative to the current directory.

Example output:

```bash
./get-taxi-preview 1.37.0
Latest snapshot version: 1.37.0-20230518.071013-9
Downloading https://repo.orbitalhq.com/snapshot/org/taxilang/taxi-cli/1.37.0-SNAPSHOT/taxi-cli-1.37.0-20230518.071013-9.zip to .taxi/
# lots of downloady and unzippy stuff
Latest taxi 1.37.0 is now available at /home/some/path/.taxi/1.37.0/taxi/bin/taxi
You can run this version by using the following alias: 
alias taxip=/home/some/path/.taxi/1.37.0/taxi/bin/taxi
```

#### Release vscode plugin

To get a personal access token, log in at [https://dev.azure.com/taxi-lang/taxi-vscode-plugin](Azure), and 
follow the instructions [here](https://code.visualstudio.com/api/working-with-extensions/publishing-extension) to create a token.

Then

```bash
cd language-server/vscode-extension

npm install
npm run sync-pom-version
npm run vsce:package
npm run vsce:publish
```


