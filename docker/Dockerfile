#FROM adoptopenjdk/openjdk11:alpine
FROM openjdk:17-oracle

# Install libstdc++
RUN microdnf update -y && microdnf install -y libstdc++

WORKDIR /app

ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} app.jar
COPY pom.xml pom.xml

EXPOSE 8080

ENTRYPOINT exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar