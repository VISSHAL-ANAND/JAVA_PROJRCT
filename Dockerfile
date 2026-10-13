FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
COPY tests ./tests
COPY resources ./resources
RUN mvn --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV PORT=8080
COPY --from=build /workspace/target/campusos-1.0-SNAPSHOT.jar /app/campusos.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/campusos.jar"]
