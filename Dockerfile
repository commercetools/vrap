FROM openjdk:8-jre-alpine

WORKDIR /app

ADD build/libs/vrap-all.jar /app/vrap.jar

EXPOSE 5050
ENTRYPOINT ["java"]
CMD ["-jar", "vrap.jar"]