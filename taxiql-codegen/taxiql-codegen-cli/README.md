This contains the code to generate type safe typescript queries from Taxi code.

It's packaged as a native app (using GraalVM), and ships as a command line tool

## Usage

Output help:
```bash
taxiql-typescript-codegen -h
```

Example:

```bash
taxiql-typescript-codegen --config "taxi/taxi.conf" --path "ts/**/*.taxi" --output "generated/" --source "query FindAllFilms { find { Film[] } }" --source "query FindFilm { find { Film } }"
```

where:
 * `config`: Either a path to a taxi.conf file, or a url to load a schema from
 * `path`: A glob of taxi files to read. Code will be emitted for every query found under this path
 * `output` A directory to write code to
 * `source` (optional, repeatable): Taxi source 

## Building a native image

You need to build against a JDK with GraalVM, such as `21.0.4-graal`.

Using SDK man:

```bash
sdk install java 21-graal
```

Then compile with Maven:

```bash
mvn package -Pnative
```

Will create `target/taxiql-typescript-codegen`, a native executable.

