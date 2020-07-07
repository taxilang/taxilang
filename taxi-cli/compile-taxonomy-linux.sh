if [ $# -eq 0 ]
then
   echo "Please provide taxonomy path."
else
   docker run -it -v "$1":/taxi taxi build
fi
