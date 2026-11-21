# syntax=docker/dockerfile:1

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
# Đảm bảo mvnw chạy được kể cả khi checkout trên Windows (CRLF).
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src src
# Cache ~/.m2 giữa các lần build để không tải lại dependency.
# Bỏ qua test vì test cần PostgreSQL; test chạy riêng (xem README).
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests clean package \
    && cp target/*.jar application.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
LABEL authors="khoa0"

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/application.jar application.jar

USER spring
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
