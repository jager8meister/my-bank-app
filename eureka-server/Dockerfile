# syntax=docker/dockerfile:1
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build

RUN apk add --no-cache maven

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -B -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /workspace/eureka-server/target/*.jar app.jar

RUN chown -R spring:spring /app

USER spring

EXPOSE 8761

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
