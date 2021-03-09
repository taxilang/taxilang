---
title: 'Getting started' description: Get up and running with Taxi
---

To get started you need to download and install Taxi. Download
the [taxi.zip](https://gitlab.com/taxi-lang/taxi-lang/-/jobs/artifacts/master/raw/taxi-cli/target/taxi-cli.zip?job=publish-release)
file and extract it.

The zip contains the files needed to run taxi, along with some scripts to make launching easier.

Once the file is downloaded and extracted, you need to add it to your path. It will look something like this:

```text
/install-location/taxi/bin
```

Once the install location has been added to your path, test everything is up and running by invoking:

```bash
taxi
```

You should see some the current version, along with some help text.


## Creating a taxi project from the command line

We are going to create an application from the command line and add taxi into that application. First steps are to `cd` into the directory you want your application to be created in.

Now, let's look at creating an application. Run the following command:

```bash
mkdir hello-world
cd hello-world
taxi init
```

You'll be prompted with basic project details to name and create your new taxi project

* Project group: `demo` ⏎
* Project name: `hello-world` ⏎
* Project version: ⏎  (Defaults are fine)
* Source directory:  ⏎ (Defaults are fine) 

A new `taxi.conf` has now been created, as follows: 

```text
name: demo/hello-world
version: 0.1.0
sourceRoot: src/
```

## Creating your first taxi file

Create your first Taxi file, named `person.taxi`:

```text
namespace demo 

[[ The first name of a person.  Call them this at dinner time. ]]
type FirstName inherits String

[[ The last name of a person.  Use this when they're in trouble. ]]
type LastName inherits String

[[ A unique id for a person, as each one of us is truly unique. Even you. ]]
type PersonId inherits String
    
model Person {
   id : PersonId 
   firstName : FirstName
   lastName : LastName 
}
```

And create a project file, named `taxi.conf`:

```text
name: demo/hello-world
version: 0.1.0
```

Then compile:

```bash
taxi build
```

This simply validates that there are no syntactic errors in your files.

This is enough to start building a taxonomy to describe your software. You could <Link publish this to a repository
However, nothing will have been output yet.

## Generating code and models from our taxonomy.

Now that we have a simple taxonomy and available, we can use it to generate code.

Taxi ships with a single generator - for emitting Kotlin code - however, you can author your own plugins to generate any
code you like from Taxi.

Modify the `taxi.conf` file, to add the Kotlin plugin. Simply add the following:

```hocon
plugins {
   taxi/kotlin {
      generatedTypeNamesPackageName: "demo.helloWorld"
      maven {
         artifactId: hello-world
         groupId: "demo"
      }
   }
}
```

Save, and re-run the build command

```bash
taxi build
```

And you should have some fresh Kotlin files generated, to match your Person object.

This has been a fairly simple example. Take a look at some of the language features to get a better understanding of how
Taxi can help you deliver better API documentation.
