# ────────────────────────────────────────────────
# Stage 1: Build the JAR with Maven Wrapper
# ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ────────────────────────────────────────────────
# Stage 2: Runtime (slim JRE)
# ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Security: non-root user
RUN useradd spring && chown -R spring /app
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
