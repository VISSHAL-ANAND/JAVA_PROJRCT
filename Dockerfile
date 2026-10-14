FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
COPY tests ./tests
COPY resources ./resources
RUN mvn --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --create-home campusos
WORKDIR /app
ENV PORT=8080 CAMPUSOS_ENV=production
COPY --from=build --chown=10001:10001 /workspace/target/campusos-1.0-SNAPSHOT.jar /app/campusos.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/campusos.jar"]
