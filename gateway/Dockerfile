FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
# Copia il pom.xml parent
COPY pom.xml ./pom.xml
# Copia il modulo
COPY gateway/pom.xml ./gateway/pom.xml
COPY gateway/src ./gateway/src
WORKDIR /app/gateway
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /app/gateway/target/*.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]