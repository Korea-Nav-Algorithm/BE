FROM --platform=$BUILDPLATFORM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY navigation-common navigation-common
COPY navigation-edge navigation-edge
COPY navigation-engine/build.gradle navigation-engine/build.gradle
RUN gradle :navigation-edge:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/navigation-edge/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s CMD curl -fsS http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
