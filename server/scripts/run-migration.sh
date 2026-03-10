#!/bin/bash
# 执行迁移到 Supabase
# 用法: DATABASE_URL="postgresql://..." ./run-migration.sh
# 或: 在项目根 .env 中设置 DATABASE_URL，然后从 server 目录运行

set -e
cd "$(dirname "$0")/.."
ROOT="$(cd ../.. && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a
  source "$ROOT/.env"
  set +a
fi
if [ -z "$DATABASE_URL" ]; then
  echo "错误: 需要 DATABASE_URL"
  echo "从 Supabase: Project Settings → Database → Connection string (URI) 复制"
  echo "或: export DATABASE_URL='postgresql://...'"
  exit 1
fi
psql "$DATABASE_URL" -f migrations/0001_init.sql
echo "迁移完成"
