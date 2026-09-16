# Multi-stage build: Vite UI + Spring Boot JAR for Render (runtime: docker).
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY frontend ./frontend

RUN mvn -Pwith-frontend -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/lotline-1.0.0.jar app.jar

ENV JAVA_OPTS=""
EXPOSE 8080

# Render sets PORT; Spring must bind to it.
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
