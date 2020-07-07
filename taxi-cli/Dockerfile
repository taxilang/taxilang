FROM openjdk:8-jre-alpine

VOLUME /taxi/

WORKDIR /taxi/

COPY target/taxi-cli.jar /

ENTRYPOINT ["java", "-jar", "/taxi-cli.jar"]

CMD ["build"]
