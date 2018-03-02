FROM openjdk:8 as builder

RUN curl -sL https://deb.nodesource.com/setup_9.x | bash - \
    && apt-get update && apt-get install -y nodejs yarn \
    && node -v && npm -v

WORKDIR /vrap
COPY . /vrap

RUN ./gradlew clean shadowJar

FROM openjdk:8-jre-alpine

WORKDIR /app

COPY --from=builder /vrap/build/libs/vrap-all.jar /app/vrap.jar
ADD vrap.sh /app/vrap.sh

ENV JAVA_OPTS  ""
EXPOSE 5050
EXPOSE 5005
ENTRYPOINT ["./vrap.sh"]
