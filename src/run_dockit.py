"""PyInstaller 入口，避免相对导入问题"""
import sys

# 打包后默认托盘 + 自动归档（无控制台无法 input 确认）
if getattr(sys, "frozen", False) and len(sys.argv) == 1:
    from dockit.config_path import get_config_path, ensure_config
    cfg = ensure_config(get_config_path())
    if not cfg.get("llm", {}).get("api_token"):
        # 未登录，默认打开设置
        sys.argv = [sys.argv[0], "settings"]
    else:
        # 已登录，默认托盘监听
        sys.argv = [sys.argv[0], "watch", "--tray", "--auto"]

if __name__ == "__main__":
    from dockit.main import main
    sys.exit(main())
