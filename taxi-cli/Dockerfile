FROM openjdk:8-jre-alpine

VOLUME /taxi/

WORKDIR /taxi/

COPY target/taxi-cli.jar /

CMD java -jar /taxi-cli.jar $CLI_CMD
