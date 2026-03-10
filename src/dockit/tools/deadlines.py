# deadlines.py - 期限提醒
"""检查即将到期的期限并发送桌面通知"""

import logging
from datetime import date, timedelta
from pathlib import Path

from ..db.db import get_connection, init_db

logger = logging.getLogger(__name__)


def _notify(title: str, message: str) -> None:
    """桌面通知"""
    try:
        from plyer import notification
        notification.notify(title=title, message=message, app_name="Dockit")
    except Exception as e:
        logger.warning("桌面通知失败: %s", e)


def check_deadlines(archive_dir: str | Path, days_ahead: int = 3) -> list[dict]:
    """
    检查 N 天内到期的期限，发送通知。

    Returns:
        即将到期的记录列表
    """
    archive_dir = Path(archive_dir).expanduser()
    init_db(archive_dir)
    today = date.today()
    end = today + timedelta(days=days_ahead)

    conn = get_connection(archive_dir)
    rows = conn.execute("""
        SELECT d.id, d.deadline_type, d.due_date, d.reminder_sent, c.case_number, c.cause_of_action
        FROM deadlines d
        JOIN cases c ON d.case_id = c.id
        WHERE d.is_completed = 0 AND d.reminder_sent = 0
          AND date(d.due_date) BETWEEN ? AND ?
        ORDER BY d.due_date
    """, (today.isoformat(), end.isoformat())).fetchall()
    conn.close()

    upcoming = [dict(r) for r in rows]
    if upcoming:
        msg = "\n".join(f"{r['case_number']} {r['deadline_type']}: {r['due_date']}" for r in upcoming[:5])
        if len(upcoming) > 5:
            msg += f"\n... 等共 {len(upcoming)} 项"
        _notify("Dockit 期限提醒", msg)
        logger.info("期限提醒: %d 项", len(upcoming))

        # 标记已提醒
        conn = get_connection(archive_dir)
        for r in upcoming:
            conn.execute("UPDATE deadlines SET reminder_sent = 1 WHERE id = ?", (r["id"],))
        conn.commit()
        conn.close()

    return upcoming


if __name__ == "__main__":
    import os
    import yaml

    logging.basicConfig(level=logging.INFO)
    from ..config_path import get_config_path
    config_path = get_config_path()
    with open(config_path, encoding="utf-8") as f:
        config = yaml.safe_load(f)
    raw = config["archive_dir"].replace("$HOME", os.path.expanduser("~"))
    archive_dir = str(Path(raw).expanduser())
    days = config.get("reminders", {}).get("default_days_before", 3)
    check_deadlines(archive_dir, days_ahead=days)
