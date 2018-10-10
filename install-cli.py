#!/usr/bin/env python3
import json
import os
import requests
import shutil
import tempfile
import sys


def make_executable(path):
   mode = os.stat(path).st_mode
   mode |= (mode & 0o444) >> 2  # copy R bits to X
   os.chmod(path, mode)

print("Installing taxi-cli...")

response = requests.get('https://api.bintray.com/packages/taxi-lang/releases/taxi-cli')
package = json.loads(response.text)
latestRel = package['latest_version']

print("The latest version is v" + latestRel)

downloadDir = tempfile.mkdtemp()

downloadFile = downloadDir + "/taxi-cli.jar"
link = "https://bintray.com/taxi-lang/releases/download_file?file_path=lang%2Ftaxi%2Ftaxi-cli%2F" + latestRel + "%2Ftaxi-cli-" + latestRel + ".jar"
with open(downloadFile, "wb") as f:
   print("Downloading %s" % downloadFile)
   response = requests.get(link, stream=True)
   total_length = response.headers.get('content-length')

   if total_length is None:  # no content length header
      f.write(response.content)
   else:
      dl = 0
      total_length = int(total_length)
      for data in response.iter_content(chunk_size=4096):
         dl += len(data)
         f.write(data)
         done = int(50 * dl / total_length)
         sys.stdout.write("\r[%s%s]" % ('=' * done, ' ' * (50 - done)))
         sys.stdout.flush()

print()
home = os.path.expanduser("~")
binDir = home + "/bin"
taxiHome = home + "/.taxi"
taxiSymlink = binDir + "/taxi"
destFile = taxiHome + "/taxi-cli.jar"

scriptFile = taxiHome + "/taxi"
if os.path.isfile(scriptFile):
   os.remove(scriptFile)

os.makedirs(taxiHome, exist_ok=True)
os.makedirs(home + "/bin", exist_ok=True)

with open(scriptFile, 'w') as script:
   script.writelines(["#!/bin/bash\n", 'java -jar ' + destFile + ' "$@" \n'])

# os.chmod(scriptFile, stat.S_IEXEC)
make_executable(scriptFile)


if os.path.isfile(taxiSymlink):
   os.remove(taxiSymlink)

os.symlink(scriptFile, taxiSymlink)
shutil.move(downloadFile, destFile)

print("Taxi installed at " + destFile + " and linked from " + taxiSymlink)
