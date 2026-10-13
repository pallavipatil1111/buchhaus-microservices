# syntax=docker/dockerfile:1
# One Dockerfile for every service: docker build --build-arg MODULE=order-service .
ARG MODULE=api-gateway

# ---- build: only the chosen module and what it depends on (-am builds "common" too) ----
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /app
COPY pom.xml .
COPY common common
COPY discovery-server discovery-server
COPY api-gateway api-gateway
COPY user-service user-service
COPY catalog-service catalog-service
COPY order-service order-service
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" PORT=8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=10 \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health" | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
