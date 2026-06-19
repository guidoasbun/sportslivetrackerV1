# Build from the repo root, passing producer/ as the context:
# docker build -f docker/producer.Dockerfile -t producer producer/
#
# In GitHub Actions this becomes:
# docker build -f docker/producer.Dockerfile -t $ECR_URI:$SHA producer/

FROM amazoncorretto:21-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

FROM amazoncorretto:21-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
