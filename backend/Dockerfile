
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY *.mvn* .mvn/
COPY mvnw pom.xml ./
COPY src ./src

RUN chmod +x mvnw && ./mvnw -B -DskipTests package


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache ca-certificates postgresql-client && \
    addgroup -S hookforge && \
    adduser -S hookforge -G hookforge

# Import Aiven CA into Java's trust store
COPY certs/ca.pem /tmp/aiven-ca.pem

RUN keytool -importcert \
    -noprompt \
    -trustcacerts \
    -alias aiven-kafka-ca \
    -file /tmp/aiven-ca.pem \
    -keystore "$JAVA_HOME/lib/security/cacerts" \
    -storepass changeit && \
    rm /tmp/aiven-ca.pem

COPY --from=build --chown=hookforge:hookforge \
    /workspace/target/hookforge-0.0.1-SNAPSHOT.jar app.jar

COPY --chown=hookforge:hookforge scripts /app/scripts

USER hookforge

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
