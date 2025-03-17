Hey there! 

You might see a bit of this:

```ts
import {CodegenConfig} from '../../taxiql-client/src/types'; 
```

And, you might be like.... WTF?

Well, here's the deal. You fix it then.

I've tried, and tried, and tried. And then decided to move on with life.

Here's a brief summary of what I've done:
 * I've added and configured nx, to support multi-modules across a mono-repo
 * I've configured inter-project `paths` in `tsconfig.base.json`, which should address the imports

This shit doesn't compile.

 * taxiql-codegen needs ONLY ONE interface / type from taxiql-client - `CodegenConfig`.
 * However, because of monorepo and typescript bullshittery, that means that this project has to pull in all the react deps from taxiql-client
 * So, fuck it.
