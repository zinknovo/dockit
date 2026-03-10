"""配置路径：打包后与开发时使用不同路径，开箱即用"""

import sys
from pathlib import Path

import yaml

# 打包后 API 地址写死
DEFAULT_API_BASE = "https://dockit-api.onrender.com"

DEFAULT_CONFIG = {
    "watch_dir": "$HOME/Downloads",
    "archive_dir": "$HOME/Desktop/Dockit归档",
    "extensions": [".pdf", ".docx", ".jpg", ".jpeg", ".png"],
    "filename_template": "{document_type}_{plaintiff}v{defendant}_{date}",
    "max_party_name_length": 10,
    "folder_template": "{case_number}_{cause_of_action}",
    "llm": {
        "api_base_url": DEFAULT_API_BASE,
        "api_token": "",
    },
    "prefilter": {"enabled": True},
    "mode": "confirm",
    "reminders": {"enabled": True, "default_days_before": 3},
}


def get_config_path() -> Path:
    """打包后：配置放在可执行文件同目录；开发时：项目根目录"""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).parent / "config.yaml"
    return Path(__file__).resolve().parents[2] / "config.yaml"


def ensure_config(path: Path) -> dict:
    """配置不存在则创建默认并返回"""
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            yaml.safe_dump(DEFAULT_CONFIG, f, allow_unicode=True, default_flow_style=False, sort_keys=False)
        return dict(DEFAULT_CONFIG)
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}
