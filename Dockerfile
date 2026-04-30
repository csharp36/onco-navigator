# ============================================================
# Stage 1: Build
# ============================================================
# Uses eclipse-temurin:21-jdk (Debian-based) for a reproducible build environment.
# The Maven wrapper downloads Maven at build time — no local Maven install required.
# ============================================================
FROM eclipse-temurin:21-jdk AS builder

# Install unzip — required by the Maven wrapper to extract the downloaded Maven archive.
# eclipse-temurin:21-jdk is Debian-based; unzip is not pre-installed in the slim variant.
RUN apt-get update -q && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy Maven wrapper first to leverage layer caching for dependency downloads.
# Changes to source code do not invalidate the dependency cache.
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn/

# Download all dependencies before copying source.
# This layer is cached as long as pom.xml does not change.
COPY pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source and build the fat JAR (skip tests — they run in CI, not in Docker build).
COPY src src/
RUN ./mvnw package -DskipTests -q

# ============================================================
# Stage 2: Runtime
# ============================================================
# Uses eclipse-temurin:21-jre (JRE only) for the smallest possible runtime image.
# No JDK tools (javac, jshell) in the production image reduces attack surface.
# ============================================================
FROM eclipse-temurin:21-jre AS runtime

# Create a non-root user to run the application.
# HIPAA note: running as root inside a container is a security finding.
# UID/GID 1001 is outside Debian's system user range (0-999) — using a regular user account
# with no home directory and no login shell minimizes the attack surface.
RUN groupadd --gid 1001 onconavigator \
 && useradd --uid 1001 --gid 1001 \
            --no-create-home --shell /sbin/nologin \
            onconavigator

WORKDIR /app

# Copy only the built JAR from the builder stage — no JDK, no Maven, no source.
COPY --from=builder /workspace/target/onco-navigator-*.jar app.jar

# Ensure the non-root user owns the application file.
RUN chown onconavigator:onconavigator app.jar

USER onconavigator

# Port matches server.port=8081 in application.yml.
EXPOSE 8081

# HEALTHCHECK: poll the lightweight /health endpoint every 30 seconds.
# Startup grace period: 60 seconds (Spring Boot + Temporal + DB connection initialization).
# Failure threshold: 3 consecutive failures before the container is marked unhealthy.
# Using wget (available in eclipse-temurin base) rather than curl.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/health || exit 1

# JVM flags for Java 21 in containers:
# -XX:+UseContainerSupport    — read CPU/memory limits from cgroups (not host specs)
# -XX:MaxRAMPercentage=75.0   — use 75% of container memory for the heap
# -XX:+UseZGC                 — Z Garbage Collector: low-latency, container-friendly
# -Djava.security.egd=...     — use non-blocking entropy for SecureRandom (faster startup)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseZGC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
