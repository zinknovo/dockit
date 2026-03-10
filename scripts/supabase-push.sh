#!/bin/bash
# 用 Supabase CLI 推送迁移
# 需要 .env 中设置 DATABASE_URL（Supabase Connection string URI）

set -e
cd "$(dirname "$0")/.."
if [ -f .env ]; then
  set -a
  source .env
  set +a
fi
if [ -z "$DATABASE_URL" ]; then
  echo "错误: 需要 DATABASE_URL"
  echo "Supabase: 左侧 Database → Connection string → URI 复制"
  echo "添加到 .env: DATABASE_URL=postgresql://..."
  exit 1
fi
supabase db push --db-url "$DATABASE_URL"
echo "✓ 迁移已推送"
