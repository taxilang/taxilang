@echo off
if [%1]==[] (
   echo Please provide taxonomy path.
) else (
   docker run -it -v "%1":/taxi taxi build
)
