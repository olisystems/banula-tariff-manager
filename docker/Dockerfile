FROM amazoncorretto:17.0.12-al2023-headless

WORKDIR /app

ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} app.jar
COPY pom.xml pom.xml

EXPOSE 8080

ENTRYPOINT exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar
