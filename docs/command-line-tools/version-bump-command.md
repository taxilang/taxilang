---
description: Automatically increment the version of your taxi project
---

# Version Bump command

```text
taxi version-bump major|minor|patch
```

This command increments the version defined in the `taxi.conf` file.

{% hint style="warning" %}
Running this command will change the layout of your taxi.conf file, to follow HOCON layout.

While there are no material differences between the files before and after, cosmetic changes can occur.  We're aware of this issue, and will address it in a future release.
{% endhint %}

[Semantic Versioning](https://semver.org/) principals are followed when incrementing the version.

