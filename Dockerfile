FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle/ gradle/

RUN gradle dependencies --no-daemon

COPY src/ src/

RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine AS runtime

RUN apk add --no-cache wget curl
RUN addgroup -S norma && adduser -S norma -G norma

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown norma:norma app.jar

USER norma

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/actuator/health | grep UP || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-jar", "app.jar"]
