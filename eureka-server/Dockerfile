# Multi-stage build for Maven multi-module project
# Stage 1: Build entire project with tests
FROM eclipse-temurin:21-jdk-alpine AS build
# Install Maven
RUN apk add --no-cache maven

WORKDIR /workspace

# Copy entire project (required for multi-module Maven build)
COPY . .

# Build entire project with tests
RUN mvn clean package -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy only the eureka-server JAR from build stage
COPY --from=build /workspace/eureka-server/target/*.jar app.jar

RUN chown -R spring:spring /app

USER spring

# Expose Eureka port
EXPOSE 8761

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
