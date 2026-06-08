FROM docker.io/maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY src/ src/

RUN --mount=type=secret,id=settings_xml,target=/root/.m2/settings.xml \
    mvn package -DskipTests

FROM docker.io/eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
COPY --from=builder /app/pom.xml pom.xml

USER nobody

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
