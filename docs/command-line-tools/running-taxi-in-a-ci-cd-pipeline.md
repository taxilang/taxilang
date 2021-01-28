# Running Taxi in a CI/CD pipeline

The Taxi CLI is packaged as a docker container in order to make running it within a CI/CD pipeline \(or even locally\) easier.

It's packaged and published on DockerHub [here](https://hub.docker.com/r/taxilang/taxi-cli).

```bash
docker pull taxilang/taxi-cli
```

To run, use the following format:

```bash
docker run -v "$PWD":/taxi -e CLI_CMD="build" -u "999:998" taxilang/taxi-cli
```

### Sample Gitlab Configuration

This config will use taxi's docker image to compile taxi, and then invoke Maven on the generated Kotlin code

```yaml
variables:
   # As of Maven 3.3.0 instead of this you may define these options in `.mvn/maven.config` so the same config is used
   # when running from the command line.
   # `installAtEnd` and `deployAtEnd` are only effective with recent version of the corresponding plugins.
   MAVEN_CLI_OPTS: "-s mvn-settings.xml --batch-mode --errors --fail-at-end --show-version -DinstallAtEnd=true -DdeployAtEnd=true"

cache:
    paths:
      - .npm
    key: "$CI_JOB_REF_NAME"

stages:
    - build
    - compile
    - deploy

validate-taxonomy:
    stage: build
    script:
        - docker pull taxilang/taxi-cli
        - docker run -v "$PWD":/taxi -e CLI_CMD="build" -u "999:998" taxilang/taxi-cli
    artifacts:
       paths:
          - ./dist/src/
          - ./dist/pom.xml

compile-taxonomy:
    stage: compile
    script:
       - cp mvn-settings.xml ./dist/
       - cd dist
       - 'mvn $MAVEN_CLI_OPTS install'
    dependencies:
       - validate-taxonomy
    artifacts:
       paths:
          - ./dist/target/*.jar

```



