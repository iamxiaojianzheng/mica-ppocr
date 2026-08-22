# ==========================================
# Stage 1: Build Jar using Maven & JDK 17
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# 复制 pom.xml 与源码
COPY pom.xml .
COPY mica-ppocr-core ./mica-ppocr-core
COPY mica-ppocr-structured ./mica-ppocr-structured
COPY mica-ppocr-solon-plugin ./mica-ppocr-solon-plugin
COPY mica-ppocr-spring-boot-starter ./mica-ppocr-spring-boot-starter
COPY mica-ppocr-server ./mica-ppocr-server

# 执行打包，仅打 mica-ppocr-server 可执行模块
RUN mvn clean package -pl mica-ppocr-server -am -DskipTests

# ==========================================
# Stage 2: Runtime Image (Eclipse Temurin JRE 17)
# ==========================================
FROM eclipse-temurin:17-jre

LABEL maintainer="Antigravity <dreamlu@net.dreamlu>"
LABEL description="mica-ppocr PP-OCRv6 纯 ONNXRuntime 推理 HTTP 微服务容器"

# 1. 自动替换为国内镜像源（支持 Ubuntu & Debian 镜像），大幅加速 apt-get 下载速度
RUN (sed -i 's@http://.*archive.ubuntu.com@http://mirrors.aliyun.com@g' /etc/apt/sources.list || true) \
    && (sed -i 's@http://.*security.ubuntu.com@http://mirrors.aliyun.com@g' /etc/apt/sources.list || true) \
    && (sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources || true) \
    && (sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list || true) \
    && apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    libglib2.0-0 \
    libsm6 \
    libxrender1 \
    libxext6 \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 从 builder 阶段复制可执行 jar 包
COPY --from=builder /build/mica-ppocr-server/target/mica-ppocr-server.jar /app/app.jar

# 暴露 8090 端口
EXPOSE 8090

# 默认环境变量
ENV PORT=8090 \
    JAVA_OPTS="-Xms512m -Xmx2g -Dfile.encoding=UTF-8" \
    DET_MODEL_PATH="models/ppocr-v6/tiny/det.onnx" \
    REC_MODEL_PATH="models/ppocr-v6/tiny/rec.onnx" \
    REC_CHAR_DICT_PATH="models/ppocr-v6/tiny/dict.txt" \
    USE_DOC_ORIENTATION="true" \
    DOC_ORI_MODEL_PATH="models/ppocr-v6/doc_ori/doc_ori.onnx"

# 默认健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:8090/api/ocr/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
