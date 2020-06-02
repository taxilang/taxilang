The types in src/ are specifically named so that there's 
a compile dependency where sources are referenced 
before they're compiled.

This is to ensure that dependencies are processed correctly
by the internal compiler.
