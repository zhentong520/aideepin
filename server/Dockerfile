FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./
COPY adi-common/pom.xml adi-common/
# OPT-15 批 2~6 新增模块：pom 必须全部在位，否则预热阶段 Maven 读根 pom <modules> 直接报 Child module does not exist
COPY adi-draw/pom.xml adi-draw/
COPY adi-model/pom.xml adi-model/
COPY adi-kb-rag/pom.xml adi-kb-rag/
COPY adi-chat-core/pom.xml adi-chat-core/
COPY adi-workflow/pom.xml adi-workflow/
COPY adi-chat/pom.xml adi-chat/
COPY adi-admin/pom.xml adi-admin/
COPY adi-bootstrap/pom.xml adi-bootstrap/
COPY adi-integration-test/pom.xml adi-integration-test/
COPY local-repo/ local-repo/
# 容器内构建走阿里云镜像仓：绕开代理 MITM 导致的 central TLS/PKIX 失败（详见 docker-mvn-settings.xml 注释）
COPY docker-mvn-settings.xml ./

# 容器内构建走阿里云镜像仓 + aether insecure 传输：
# 本机 Docker 出网经代理 MITM，所有 HTTPS 站点 PKIX 校验失败；insecure 模式跳过证书校验，
# 但 Maven 仍校验 artifact checksum，完整性有保障（详见 docker-mvn-settings.xml 注释）
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -s docker-mvn-settings.xml -Daether.connector.https.securityMode=insecure package -DskipTests -B

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -s docker-mvn-settings.xml -Daether.connector.https.securityMode=insecure package -DskipTests -B

FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y --no-install-recommends locales wget && rm -rf /var/lib/apt/lists/*

ENV LANG="zh_CN.UTF-8" \
    LANGUAGE="zh_CN:zh" \
    LC_ALL="zh_CN.UTF-8" \
    APP_VERSION="1.0.0-SNAPSHOT" \
    TZ="Asia/Shanghai" \
    APP_PROFILE="dev" \
    JAVA_OPTS="-Xms512m -Xmx1536m -XX:+HeapDumpOnOutOfMemoryError"

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=build /app/adi-bootstrap/target/adi-bootstrap-0.0.1-SNAPSHOT.jar /data/app/aideepin-api.jar

# OPS-02：exec 形式让 java 取代 sh 成为 PID 1，SIGTERM 才能到达 JVM 触发优雅停机
ENTRYPOINT ["sh","-c", "exec java $JAVA_OPTS -jar /data/app/aideepin-api.jar --spring.profiles.active=$APP_PROFILE"]

EXPOSE 9999
