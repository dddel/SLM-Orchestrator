# syntax=docker/dockerfile:1
# Multi-stage build per llmorchestrator (Spring Boot 4 / Java 21)

# Stage 1: Build dell'applicazione
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copia solo le dipendenze Maven per sfruttare la cache dei layer Docker
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copia i sorgenti ed esegui la compilazione del pacchetto (saltando i test)
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Immagine di runtime minimale e sicura
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Creazione di utente non privilegiato per sicurezza e conformità rootless
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copia del JAR compilato
COPY --from=builder /workspace/target/*.jar app.jar

# Configurazione permessi e utente
RUN chown -R appuser:appgroup /app
USER appuser:appgroup

# Configurazione porta standard e flag JVM per container
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
