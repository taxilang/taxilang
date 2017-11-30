## Converters

This project contains converters between Taxi and other resource description formats (swagger, RAML)

The goal is to support a wide variety of languages, 
and for Taxi to be interoperable with other standards via strong conversion support.

Taxi will have first-class support for languages initially via `Taxi -> RAML -> ${targetLanguage}`,
as RAML has the best support for meta-descriptions of structures.

In time, it's expected to replace RAML support with first-class language generators.

