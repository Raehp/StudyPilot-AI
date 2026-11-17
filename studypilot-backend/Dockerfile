# ---- 构建阶段：Maven + JDK17 打包可执行 jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---- 运行阶段：JRE17 ----
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/studypilot-backend-0.0.1-SNAPSHOT.jar app.jar
# 文件存储目录（持久化卷挂载点）；密钥/密码全部由环境变量注入，绝不写入镜像
ENV STORAGE_DIR=/app/data/uploads \
    JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]