FROM openjdk:8-jre-alpine

WORKDIR /app

ADD build/libs/vrap-all.jar /app/vrap.jar
ADD vrap.sh /app/vrap.sh

ENV JAVA_OPTS  ""
EXPOSE 5050
EXPOSE 5005
ENTRYPOINT ["./vrap.sh"]
