if [ $# -eq 0 ]
then
   echo "Please provide taxonomy path."
else
   docker run -it -v "$1":/taxi -e CLI_CMD="build" taxi-cli
fi
