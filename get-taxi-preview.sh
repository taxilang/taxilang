#!/bin/bash

# check if the script received an argument
if [ $# -eq 0 ]; then
    echo "No arguments supplied. Please provide a version number."
    exit 1
fi

versionNumber=$1
metadataURL="https://repo.orbitalhq.com/snapshot/org/taxilang/taxi-cli/${versionNumber}-SNAPSHOT/maven-metadata.xml"

# use curl to fetch the metadata file and grep + sed to parse out the timestamp and build number
timestamp=$(curl -s $metadataURL | grep '<timestamp>' | sed 's/.*<timestamp>\([^<]*\)<\/timestamp>.*/\1/' | head -n 1)
buildNumber=$(curl -s $metadataURL | grep '<buildNumber>' | sed 's/.*<buildNumber>\([^<]*\)<\/buildNumber>.*/\1/' | head -n 1)

latestSnapshot="${versionNumber}-${timestamp}-${buildNumber}"
echo "Latest snapshot version: ${latestSnapshot}"

# construct the download URL
downloadURL="https://repo.orbitalhq.com/snapshot/org/taxilang/taxi-cli/${versionNumber}-SNAPSHOT/taxi-cli-${latestSnapshot}.zip"

# check if the .taxi directory exists. If not, create it
if [ ! -d ".taxi" ]; then
  mkdir .taxi
fi

echo "Downloading $downloadURL to .taxi/"

# use curl to download the file
zipFile=".taxi/taxi-cli-${latestSnapshot}.zip"
curl -o $zipFile $downloadURL

# unzip the downloaded file
unzip -o $zipFile -d .taxi/$versionNumber

cliPath=".taxi/$versionNumber/taxi/bin/taxi"
chmod +x $cliPath

echo "Latest taxi $versionNumber is now available at $(pwd)$cliPath"
echo "You can run this version by using the following alias: "
echo "alias taxip=$(pwd)/$cliPath"
