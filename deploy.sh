#!/bin/bash
# Dockit 一键部署脚本：编译、构建镜像、启动全部服务（docker compose v2）

set -euo pipefail

cd "$(dirname "$0")"

echo "=== Dockit 项目部署脚本 ==="

# 1. 编译（没有 jar 才编译）
if [ ! -f ai-service/target/ai-service-1.0.0.jar ]; then
  echo ">>> 编译项目..."
  mvn clean package -DskipTests
fi

# 2. 构建 Docker 镜像
echo ">>> 构建 Docker 镜像..."
for service in user file ai document gateway; do
  docker build -q -t "dockit/${service}-service:1.0.0" "${service}-service/"
done

# 3. 一键启动（基础设施 + 微服务，数据库由 mysql-schema.sql 自动初始化）
echo ">>> 启动所有服务..."
docker compose --env-file .env up -d

echo ""
echo "=== 部署完成 ==="
echo "  网关:      http://localhost:8080"
echo "  Swagger:   http://localhost:8080/swagger-ui.html"
echo "  Nacos:     http://localhost:8848/nacos"
echo "  MinIO:     http://localhost:9001"
echo "  RabbitMQ:  http://localhost:15672"
