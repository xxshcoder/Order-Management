# syntax=docker/dockerfile:1
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/order-service/pom.xml services/order-service/
COPY services/inventory-service/pom.xml services/inventory-service/
COPY services/notification-service/pom.xml services/notification-service/
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY services/inventory-service/src services/inventory-service/src
RUN mvn -B -q -pl services/inventory-service -am package -DskipTests
RUN mkdir -p /workspace/extracted && cd /workspace/extracted \
    && java -Djarmode=layertools -jar /workspace/services/inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar extract

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
