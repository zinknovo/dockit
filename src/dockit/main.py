#!/usr/bin/env python3
# main.py - 入口，启动文件夹监听
"""Dockit - 法律文书自动归档工具"""

from pathlib import Path
from dotenv import load_dotenv

# 优先从项目根目录的 .env 加载环境变量
_load_env = Path(__file__).resolve().parents[2] / ".env"
load_dotenv(_load_env)

import logging
import os
import sys
from datetime import datetime

import yaml

from .core.watcher import start_watching
from .core.prefilter import load_prefilter_stats

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


def load_config(config_path: str | Path = None) -> dict:
    """加载 YAML 配置"""
    if config_path is None:
        config_path = Path(__file__).resolve().parents[2] / "config.yaml"
    path = Path(config_path)
    if not path.exists():
        raise FileNotFoundError(f"配置文件不存在: {path}")
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def _log_time():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _print_doc_info(doc_info) -> None:
    """打印识别结果"""
    print(f"  {1}. 文书类型: {doc_info.document_type}")
    print(f"  {2}. 案号: {doc_info.case_number or '（未识别）'}")
    print(f"  {3}. 案由: {doc_info.cause_of_action or '（未识别）'}")
    print(f"  {4}. 原告: {doc_info.plaintiff or '（未识别）'}")
    print(f"  {5}. 被告: {doc_info.defendant or '（未识别）'}")
    print(f"  {6}. 开庭时间: {doc_info.hearing_time or '（未识别）'}")
    if doc_info.hearing_location:
        print(f"  开庭地点: {doc_info.hearing_location}")
    if doc_info.document_date:
        print(f"  文书日期: {doc_info.document_date}")


FIELD_NAMES = {
    "1": "document_type",
    "2": "case_number",
    "3": "cause_of_action",
    "4": "plaintiff",
    "5": "defendant",
    "6": "hearing_time",
    "7": "hearing_location",
    "8": "document_date",
}

FIELD_LABELS = {
    "1": "文书类型",
    "2": "案号",
    "3": "案由",
    "4": "原告",
    "5": "被告",
    "6": "开庭时间",
    "7": "开庭地点",
    "8": "文书日期",
}


def _make_on_classified(config: dict):
    """生成 confirm 模式下的 on_classified 回调"""
    mode = config.get("mode", "confirm")
    archive_dir = str(Path(config["archive_dir"]).expanduser())

    def on_classified(doc_info, target_path, text):
        if mode == "auto":
            return True, None  # 自动模式直接确认

        print("\n[确认模式] 识别结果:")
        _print_doc_info(doc_info)
        print(f"\n归档到: {target_path}")
        print("操作: [y]确认 / [e]编辑字段 / [s]跳过")
        while True:
            choice = input("> ").strip().lower()
            if choice == "y":
                return True, None
            if choice == "s":
                return False, None
            if choice == "e":
                correction = _do_edit(doc_info)
                if correction is not None:
                    return True, correction  # 编辑完成，继续归档（后续会二次确认）
                # 用户取消编辑，重新选择
                print("操作: [y]确认 / [e]编辑字段 / [s]跳过")
                continue
            print("请输入 y / e / s")

    return on_classified


def _do_edit(doc_info) -> dict | None:
    """交互式编辑字段，返回修正字典"""
    correction = {}
    num = None
    while True:
        if num is None:
            num = input("修改哪个字段？(输入编号): ").strip()
        if num not in FIELD_NAMES:
            print("无效编号，请输入 1-8")
            num = None
            continue
        field = FIELD_NAMES[num]
        label = FIELD_LABELS[num]
        current = getattr(doc_info, field, None) or "（空）"
        print(f"当前值: {current}")
        new_val = input("新值: ").strip()
        if new_val:
            correction[field] = new_val
            print(f"[已记录修正] {current} → {new_val}")
        num = None
        more = input("还要修改其他字段吗？(输入编号，或回车完成): ").strip()
        if not more:
            return correction if correction else None
        if more in FIELD_NAMES:
            num = more


def _make_on_confirm(config: dict):
    """生成编辑后的二次确认回调"""
    mode = config.get("mode", "confirm")

    def on_confirm(doc_info, target_path):
        if mode == "auto":
            return True
        print(f"\n归档到: {target_path}")
        while True:
            ans = input("确认归档？(y/n): ").strip().lower()
            if ans == "y":
                return True
            if ans == "n":
                return False
            print("请输入 y 或 n")

    return on_confirm


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser(description="Dockit 法律文书自动归档")
    parser.add_argument("cmd", nargs="?", default="watch", help="watch|calendar|timeline|prefilter-stats|settings")
    parser.add_argument("--auto", action="store_true", help="自动模式，静默归档")
    parser.add_argument("--confirm", action="store_true", help="确认模式，每次归档前确认")
    parser.add_argument("--tray", action="store_true", help="系统托盘模式")
    args = parser.parse_args()

    config_path = Path(__file__).resolve().parents[2] / "config.yaml"
    config = load_config(config_path)

    # 扩展路径
    def _expand_path(p: str) -> str:
        p = p.replace("$HOME", os.path.expanduser("~"))
        return str(Path(p).expanduser())
    config["watch_dir"] = _expand_path(config["watch_dir"])
    config["archive_dir"] = _expand_path(config["archive_dir"])

    if args.cmd == "settings":
        from .ui.gui_settings import run_settings
        run_settings()
        return 0
    if args.cmd == "prefilter-stats":
        archive_dir = _expand_path(config["archive_dir"])
        stats = load_prefilter_stats(archive_dir)
        print("预筛统计 (archive_dir/prefilter_log.jsonl):")
        print(f"  总文件数: {stats['total']}")
        print(f"  通过(进入LLM): {stats['passed']}")
        print(f"  跳过: {stats['rejected']}")
        if stats.get("by_reason"):
            print("  按原因:")
            for k, v in sorted(stats["by_reason"].items(), key=lambda x: -x[1]):
                print(f"    {k}: {v}")
        return 0
    if args.cmd == "calendar":
        from .ui.views import calendar_text
        print(calendar_text(config["archive_dir"]))
        return 0
    if args.cmd == "timeline":
        from .ui.views import timeline_text
        print(timeline_text(config["archive_dir"]))
        return 0

    # watch 模式：必须先配置 api_base_url + api_token
    llm = config.get("llm") or {}
    api_base = llm.get("api_base_url") or os.environ.get("DOCKIT_API_BASE_URL")
    api_token = llm.get("api_token") or os.environ.get("DOCKIT_API_TOKEN")
    if not api_base or not api_token:
        logger.error("未配置后端 API。请在 config.yaml 的 llm 下设置 api_base_url 和 api_token，或设置环境变量 DOCKIT_API_BASE_URL、DOCKIT_API_TOKEN")
        logger.error("请先登录获取 token：curl -X POST <api_base>/api/auth/login -H 'Content-Type: application/json' -d '{\"email\":\"...\",\"password\":\"...\"}'")
        return 1

    if args.auto:
        config["mode"] = "auto"
    elif args.confirm:
        config["mode"] = "confirm"

    on_classified = _make_on_classified(config)
    on_confirm = _make_on_confirm(config)

    if args.tray:
        try:
            from .ui.tray import run_tray
            observer = start_watching(config, on_classified, on_confirm)
            stop_flag = []

            def stop_watcher():
                stop_flag.append(1)
                observer.stop()

            def run_watcher():
                observer.join()

            import threading
            t = threading.Thread(target=run_watcher, daemon=True)
            t.start()
            run_tray(config, stop_watcher)
        except ImportError as e:
            logger.warning("托盘需要 pystray Pillow: %s", e)
            return 1
    else:
        logger.info("Dockit 启动，模式: %s", config.get("mode", "confirm"))
        observer = start_watching(config, on_classified, on_confirm)
        try:
            observer.join()
        except KeyboardInterrupt:
            logger.info("收到退出信号，正在停止...")
            observer.stop()
            observer.join()
            logger.info("已退出")

    return 0


if __name__ == "__main__":
    sys.exit(main())
