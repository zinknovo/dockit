#!/usr/bin/env bash
# 验证 dockit 客户端与核心流程
set -e

cd "$(dirname "$0")/.."

echo "=== 1. 客户端单元测试（排除 slow） ==="
uv run pytest tests/ -v -m "not slow" --tb=short

echo ""
echo "=== 2. 假传票 E2E ==="
uv run python scripts/gen_test_pdf.py tests/fixtures/test_传票.pdf 2>/dev/null || true
uv run pytest tests/test_e2e.py -v --tb=short

echo ""
echo "=== 3. 覆盖率（目标 90%） ==="
uv run pytest tests/ -m "not slow" --cov=src/dockit --cov-report=term-missing -q

echo ""
echo "=== 验证完成 ==="
echo "完整流程："
echo "  1. 后端: cd server && uv sync && uv run uvicorn main:app --host 127.0.0.1 --port 8000"
echo "  2. 设置: uv run dockit settings"
echo "  3. 监听: uv run dockit --confirm 或 uv run dockit --tray"
