#!/usr/bin/env bash
# 打包 Dockit 桌面客户端 (PyInstaller)
# 输出: dist/dockit/
set -e

cd "$(dirname "$0")/.."

# 安装 PyInstaller
uv sync --extra build 2>/dev/null || uv pip install pyinstaller

# 确保 src 在路径中
export PYTHONPATH="${PYTHONPATH:+$PYTHONPATH:}src"

# 打包 (onedir 模式，CustomTkinter 需要)
uv run pyinstaller dockit.spec --noconfirm --clean

echo ""
echo "=== 打包完成 ==="
echo "输出目录: dist/dockit/"
echo "  - macOS: dist/dockit/dockit"
echo "  - Windows: dist/dockit/dockit.exe"
echo ""
echo "首次运行会自动创建配置。macOS: ~/Library/Application Support/Dockit/config.yaml"
