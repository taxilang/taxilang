---
description: Generates Kotlin code from your Taxi project
---

# Kotlin Plugin

### Overview

The Kotlin Plugin generates Kotlin classes, annotated with `@DataType`.  This makes referencing taxi types in services more typesafe and robust.

### What's generated

#### Scalar & Primitve types

Primitve and Scalar \(types without propreties\) are output as kotlin `type alias` types, with a corresponding `@DataType` annotation

#### Model types

`model` types in Taxi \(or types that contain properties\) will generate a kotlin `data class`. with a correspodning `@DataType` annotation

#### Enums

Enums in Taxi are output as kotlin `enum`classes

### Configuration

| Name | Usage | Default |
| :--- | :--- | :--- |
| outputPath | Defines where the generated kotlin code is written.  This is relative to the output parameter in the main project config | kotlin |
| kotlinVersion | The version of kotlin to use.  Defines the Maven dependencies that are generated | 1.3.50 |
| kotlinLanguageVersion | The kotlin language version.  Corresponds to the [languageVersion](https://kotlinlang.org/docs/reference/using-maven.html#attributes-common-for-jvm-and-js) property in Kotlins configuration. | 1.3 |
| jvmTarget | Which version of the JVM to target.  Defaults t | 1.8 |
| maven | Defines the properties used for Maven cofniguration.  If omitted, a `pom.xml` file is not generated. | - |
| taxiVersion | The version of the taxi to depend on | The same version as the compiler being used. |

For more details see the [KotlinPluginConfig](https://gitlab.com/taxi-lang/taxi-lang/-/blob/develop/taxi-cli/src/main/java/lang/taxi/cli/plugins/internal/KotlinPlugin.kt#L123) class.

### Generating a Maven pom.xml

The plugin will optionally generate a Maven `pom.xml` file, to allow you to then compile and publish a jar file containing the generated artifacts.

{% hint style="info" %}
The plugin will generate a valid pom.xml file, but doesn't attempt to run maven to compile the generated code.  That's left to you, or your build server configuration.
{% endhint %}

The following properties can be configured, which relate to the same concepts in a maven pom.xml file:

* groupId \(defaults to the organisation name within the taxi project\)
* artifactId \(defaults to the project name within the taxi project\)
* dependencies
* repositories
* distributionManagemen

| Name | Usage | Default value |
| :--- | :--- | :--- |
| groupId | The maven groupId in the generated output | The groupId of the taxi project |
| artifactId | The maven artifactId in the generated output | The project name of the taxi project |
| dependencies | Configures additional maven dependencies in the output maven pom.  Generally, this isn't required.  Kotlin and Taxi dependencies are included by default | Kotlin and Taxi dependencies |
| repositories | Configures additional repositories to download dependencies from.  Use this if you have an internal maven repository server that operates as a coprorpate proxy to Maven central | Omitted |
| distributionManagement | Defines where generated jar files are published using mavens deploy command | Omitted |

#### Sample Configuration

```text
name: taxi/maven-sample
version: 0.3.0
sourceRoot: src/
plugins: {
   taxi/kotlin: {
      maven: {
         groupId: "lang.taxi"
         artifactId: "parent"
         repositories: [
            {
               id: "internal-repo"
               url: "https://newcorp.nexus.com"
               snapshots: true
            }
         ]
         distributionManagement: {
            id: "some-internal-repo"
            url: "https://our-internal-repo"
         }
      }
   }
}

```

