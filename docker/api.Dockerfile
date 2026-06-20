# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy pom.xml first to cache our downloaded dependencies!
# This makes future builds much faster.
COPY api/pom.xml .
RUN mvn dependency:go-offline

# Copy the source code and compile it
COPY api/src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Only copy the compiled jar file from Stage 1
COPY --from=builder /app/target/api.jar app.jar

# Security best practice: Run as a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Expose our API port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
