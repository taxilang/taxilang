## Compiling Taxonomy
This project uses a built in Docker image to compile taxonomies.
Before running the image make sure that file sharing has been configured correctly in docker settings:
Running the container without providing a user would result some IO access errors.<br/>
To use the username of the local user, use -u with your run script.<br/>
ex: docker run -v "$PWD":/taxi -u user1 taxilang/taxi command<br/>
If you don't specify any commands, the default behaviour of the docker image to build.

#### For Windows users:
Go to Settings -> Resources -> File Sharing

Add taxonomy root path to the list.

#### For Linux and MacOs users:
No additional settings required.
<br/><br/>

### Running the script
Use 'compile-taxonomy-linux.sh' or 'compile-taxonomy-windows.cmd' according to your operation system.
Provide the path which contains the taxonomy sources while running the script.
<br/>
Docker accepts paths as in Unix format, so use forward slash as path separator.

*ex: compile-taxonomy-linux.sh /opt/taxi/taxonomy/*

#####Using custom taxi-cli options is also possible:
Open the script file.<br/>
Insert your cli command into double quotes following **CLI_CMD** argument.

*ex: docker run -it -v "$PWD":/taxi -e **CLI_CMD="-d build"** taxi-cli*

#####Output:
Generated sources will be found in **'dist'** directory at the same location as the volume used within the image.
There will be generated taxonomy files and pom.xml which makes it possible to compile using Maven.

Run 'mvn install'.
