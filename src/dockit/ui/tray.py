# tray.py - 系统托盘
"""pystray 托盘图标：状态、最近归档、导出、期限提醒"""

import logging
import threading
from pathlib import Path

logger = logging.getLogger(__name__)


_current_icon = None

def notify_main(title: str, msg: str) -> None:
    """供其他模块调用发送系统通知"""
    if _current_icon:
        _current_icon.notify(title, msg)

def run_tray(config: dict, watch_callback=None) -> None:
    """
    启动系统托盘。watch_callback() 在后台运行监听（阻塞）。
    """
    global _current_icon
    try:
        import pystray
        from PIL import Image
    except ImportError as e:
        logger.warning("托盘需要: pip install pystray Pillow，跳过: %s", e)
        return

    archive_dir = str(Path(config["archive_dir"]).expanduser())

    def on_export(icon, _):
        try:
            from ..tools.export import export_court_sessions, export_deadlines
            export_court_sessions(archive_dir)
            export_deadlines(archive_dir)
            icon.notify("导出完成", "开庭记录表.xlsx、关键期限.xlsx")
        except Exception as e:
            icon.notify("导出失败", str(e)[:50])

    def on_remind(icon, _):
        try:
            from ..tools.deadlines import check_deadlines
            days = config.get("reminders", {}).get("default_days_before", 3)
            n = len(check_deadlines(archive_dir, days))
            if n > 0:
                icon.notify("期限提醒", f"{n} 项即将到期")
            else:
                icon.notify("期限提醒", "暂无即将到期的期限")
        except Exception as e:
            icon.notify("提醒失败", str(e)[:50])

    def on_settings(icon, _):
        try:
            from .gui_settings import run_settings
            import threading
            threading.Thread(target=lambda: run_settings(), daemon=True).start()
        except Exception as e:
            logger.exception("打开设置失败: %s", e)
            icon.notify("设置", str(e)[:50])

    def on_quit(icon, _):
        icon.stop()
        if watch_callback:
            watch_callback()  # 停止 watcher

    # 简单 64x64 图标
    img = Image.new("RGB", (64, 64), color=(70, 130, 180))
    menu = pystray.Menu(
        pystray.MenuItem("设置", on_settings),
        pystray.MenuItem("导出 Excel", on_export),
        pystray.MenuItem("检查期限", on_remind),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("退出", on_quit),
    )
    icon = pystray.Icon("dockit", img, "Dockit 运行中", menu)
    _current_icon = icon

    # watch_callback 仅在退出时调用以停止 watcher，勿在启动时调用
    def auto_check():
        import time
        while icon.visible:
            # 每天 09:00 或归档成功后 1 小时检查一次（简单模拟）
            on_remind(icon, None)
            time.sleep(3600 * 4)  # 每 4 小时静默检查一次

    threading.Thread(target=auto_check, daemon=True).start()
    icon.run()
