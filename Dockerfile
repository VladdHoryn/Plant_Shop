# syntax=docker/dockerfile:1
# One Dockerfile for every module:
#   docker build --build-arg SERVICE=catalog-service --build-arg APP_VERSION=1.0.0 -t plantshop/catalog-service:1.0.0 .

FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY pom.xml .
COPY config-service/pom.xml config-service/
COPY discovery-service/pom.xml discovery-service/
COPY api-gateway/pom.xml api-gateway/
COPY catalog-service/pom.xml catalog-service/
COPY inventory-service/pom.xml inventory-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY ${SERVICE}/src ${SERVICE}/src
# The Maven repository cache is shared by all service builds, so dependencies download once
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${SERVICE} -am package -DskipTests \
    && cp ${SERVICE}/target/${SERVICE}-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /workspace/app.jar app.jar
USER spring
# Declared in the runtime stage only, so changing the version does not rebuild the jar
ARG APP_VERSION=dev
LABEL org.opencontainers.image.version=${APP_VERSION}
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    APP_VERSION=${APP_VERSION}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
