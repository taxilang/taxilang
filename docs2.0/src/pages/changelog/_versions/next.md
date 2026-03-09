---
version: next
aggregateVersion: 'next'
releaseDate: 2026-03-09
title: next release notes
---
## next
2026-03-09

### Bug Fixes

* **assignability:** fix isAssignable() for arrays when not using structural compatibility ([0b3270c](https://gitlab.com/taxi-lang/taxi-lang/commit/0b3270c184208900fbfb907d423fb18c5d06e5bd))
* **compiler:** fix incorrect "Enum does not contain a member of" errors ([671aa45](https://gitlab.com/taxi-lang/taxi-lang/commit/671aa45bc4f32e8e589c46405c6f1d746bc25792))
* **compiler:** fix incorrect error in else block type matching ([3dfdbdb](https://gitlab.com/taxi-lang/taxi-lang/commit/3dfdbdb45ca6dd8cb64b4e31c4b9861eaf14bf23))
* **core:** memoize referencedTypes as previous implementation lead to excessive memory allocation ([36a5f4b](https://gitlab.com/taxi-lang/taxi-lang/commit/36a5f4b759f4e8937a55c6043f1db767e0e9eb24))
* **core:** move unescaping of reserved words to the grammar ([6f9bffb](https://gitlab.com/taxi-lang/taxi-lang/commit/6f9bffb4e923268ab19ad6a9b40ccb6ef9d5b6dd))
* **http:** added missing verbs OPTIONS and HEAD ([2167efb](https://gitlab.com/taxi-lang/taxi-lang/commit/2167efb7cdb71a005b903dcc08686a292a6f1325))
* **package-manager:** clean up git workspace directory path resolution ([b22d98b](https://gitlab.com/taxi-lang/taxi-lang/commit/b22d98bf6ac565a3ace70fba84e3f2d803da00b7))


### Features

* **compiler:** restore duplicate type and service error detection ([b8015d9](https://gitlab.com/taxi-lang/taxi-lang/commit/b8015d92c273981fb9e5a98ca5f4edf8c1ea723f))
* **core:** support merging and serialization of compiler options ([7a50496](https://gitlab.com/taxi-lang/taxi-lang/commit/7a50496fc82b61fb62fb71c01a2bf07538c051cc))
* **language-server:** add context help and diagnostics for taxi.conf files ([1de0eab](https://gitlab.com/taxi-lang/taxi-lang/commit/1de0eaba91ecebe29027caeed3369d55c601e643))
* **stdlib:** add parseJson function ([687f456](https://gitlab.com/taxi-lang/taxi-lang/commit/687f456aba68549f42176aa3dd28bcc4f481c1e4))
* support filtering of imports in the compiler ([5027557](https://gitlab.com/taxi-lang/taxi-lang/commit/5027557f8a9ffda5f67d9c8467ea4f7442529abc))



