FROM openjdk:8-jre-alpine

WORKDIR /app

ADD build/libs/src-all.jar /app/ramble.jar

EXPOSE 5050
ENTRYPOINT ["java", "-jar", "ramble.jar"]