#!/bin/bash
set -e

echo "=== Dockit 本地部署 ==="

# 1. 编译（没 jar 才编）
if [ ! -f ai-service/target/ai-service-1.0.0.jar ]; then
  echo ">>> 编译项目..."
  mvn clean package -DskipTests
fi

# 2. 构建镜像（如果镜像不存在或源码更新了）
echo ">>> 构建 Docker 镜像..."
docker build -q -t dockit/user-service:1.0.0 user-service/
docker build -q -t dockit/file-service:1.0.0 file-service/
docker build -q -t dockit/ai-service:1.0.0 ai-service/
docker build -q -t dockit/document-service:1.0.0 document-service/
docker build -q -t dockit/gateway-service:1.0.0 gateway-service/

# 3. 一键启动
echo ">>> 启动所有服务..."
docker compose --env-file .env up -d

echo ""
echo "=== 启动完成 ==="
echo "  网关:      http://localhost:8080"
echo "  Swagger:   http://localhost:8080/swagger-ui.html"
echo "  Nacos:     http://localhost:8848/nacos"
echo "  MinIO:     http://localhost:9001"
echo "  RabbitMQ:  http://localhost:15672"
echo ""
echo "  监控（可选）: docker compose --env-file .env --profile monitoring up -d"
echo "  查看状态:     docker compose ps"
echo "  查看日志:     docker compose logs -f"
