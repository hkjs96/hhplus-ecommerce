FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar -x test

RUN set -eux; \
  JAR="$(ls -1 build/libs/*.jar | grep -v -- '-plain\\.jar$' | head -n 1)"; \
  cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

ARG OTEL_AGENT_VERSION=2.12.0
RUN set -eux; \
  mkdir -p /otel; \
  curl -fsSL -o /otel/opentelemetry-javaagent.jar \
    "https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar"

COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

