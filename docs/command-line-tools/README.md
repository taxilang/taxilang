---
description: 'An overview of the `taxi` command line tool, and it''s config'
---

# Command line tools

## taxi cli [ ![Download](https://api.bintray.com/packages/taxi-lang/releases/taxi-cli/images/download.svg) ](https://bintray.com/taxi-lang/releases/taxi-cli/_latestVersion)

The `taxi` command line tool provides access to the compiler - which validates the syntax of taxi projects  - and allows plugins of generators to create models and services in different langauges and frameworks.

## Getting started

Download and install the taxi command line tool by running the following:

```bash
 curl -s "https://gitlab.com/taxi-lang/taxi-lang/raw/master/install-cli.py" | python3
```

This will install the `taxi` command line tool.

## Project overview

A typical taxi project will be laid out as follows:

```text
project/
├── src/
│   ├── someTypes.taxi
│   └── moreTypes.taxi
└── taxi.conf
```

## taxi.conf

A `taxi.conf` file describes a project's layout, and the plugins to be invoked after compilation.  It follows the [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md#hocon-human-optimized-config-object-notation) format, which is like supercharged JSON.

Read more details about the Taxi.Conf file here:

{% page-ref page="taxi.conf-file.md" %}

## Plugins

Taxi's compiler and language generators are extensible and pluggable. 

You can leverage our existing plugins to generate code, or author your own.  Read more about plugins here:

{% page-ref page="plugins/" %}





