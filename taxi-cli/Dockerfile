FROM openjdk:8-jre-alpine

VOLUME /taxi/

WORKDIR /taxi/

COPY target/appassembler/ /taxi-cli/

ENTRYPOINT ["/taxi-cli/bin/taxi"]

CMD ["build"]
