"""PyInstaller 入口，避免相对导入问题"""
import sys

# 打包后默认托盘 + 自动归档（无控制台无法 input 确认）
if getattr(sys, "frozen", False):
    if "watch" not in sys.argv and "settings" not in sys.argv and "calendar" not in sys.argv:
        sys.argv = [sys.argv[0], "watch", "--tray", "--auto"]

if __name__ == "__main__":
    from dockit.main import main
    sys.exit(main())
