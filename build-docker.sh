#!/bin/bash
# 构建全部服务镜像

set -euo pipefail

cd "$(dirname "$0")"

for service in user file ai document gateway; do
  echo ">>> 构建 dockit/${service}-service:1.0.0"
  docker build -t "dockit/${service}-service:1.0.0" "${service}-service/"
done

echo "全部镜像构建完成"
