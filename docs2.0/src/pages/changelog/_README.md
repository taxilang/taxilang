This folder contains the changelog

 - _versions are the raw files output by changelog-generator.
   - These files aren't modified by scripts once created, making it safe to clean them up once the script has run
   - They serve as the input into changelog-aggregator
   - next is ALWAYS overwritten
   - this directory is not served
 - `releases/` contains the aggergated pages - the output of changelog-aggregator
   - We bundle minor and patch versions together
