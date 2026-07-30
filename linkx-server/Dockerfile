# =============================================================================
# LinkX Server 多阶段构建
# -----------------------------------------------------------------------------
# 构建：docker build -t linkx-server:local .
# 运行：docker compose --profile app up -d
# 说明：见 docs/OPS.md
# =============================================================================

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package \
    && cp target/linkx-server-*.jar /workspace/app.jar

FROM eclipse-temurin:21.0.6_7-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system --gid 10001 linkx \
    && useradd --system --uid 10001 --gid linkx --home-dir /app --shell /usr/sbin/nologin linkx \
    && mkdir -p /app/logs \
    && chown -R linkx:linkx /app

COPY --from=build --chown=linkx:linkx /workspace/app.jar /app/app.jar

USER linkx
EXPOSE 8080 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080"]

ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
