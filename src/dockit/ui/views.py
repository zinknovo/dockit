# views.py - 期限日历、案件时间线
"""终端表格或简单 HTML 视图"""

import logging
from pathlib import Path

from ..db.db import get_connection, init_db

logger = logging.getLogger(__name__)


def calendar_text(archive_dir: str | Path, month: str | None = None) -> str:
    """期限日历（终端文本表格）"""
    archive_dir = Path(archive_dir).expanduser()
    init_db(archive_dir)
    conn = get_connection(archive_dir)
    rows = conn.execute("""
        SELECT c.case_number, d.deadline_type, d.due_date, d.is_completed
        FROM deadlines d
        JOIN cases c ON d.case_id = c.id
        ORDER BY d.due_date
    """).fetchall()
    conn.close()

    lines = ["案号 | 期限类型 | 到期日 | 状态", "-" * 50]
    for r in rows:
        status = "已完成" if r["is_completed"] else "待办"
        lines.append(f"{r['case_number'][:20]} | {r['deadline_type']} | {r['due_date']} | {status}")
    return "\n".join(lines) if lines else "暂无期限记录"


def timeline_text(archive_dir: str | Path, case_number: str | None = None) -> str:
    """案件时间线"""
    archive_dir = Path(archive_dir).expanduser()
    init_db(archive_dir)
    conn = get_connection(archive_dir)
    if case_number:
        rows = conn.execute("""
            SELECT doc.document_type, doc.uploaded_at, s.session_time, s.session_result
            FROM documents doc
            LEFT JOIN court_sessions s ON s.source_document_id = doc.id
            JOIN cases c ON doc.case_id = c.id
            WHERE c.case_number = ?
            ORDER BY doc.uploaded_at
        """, (case_number,)).fetchall()
    else:
        rows = conn.execute("""
            SELECT c.case_number, doc.document_type, doc.uploaded_at, s.session_time
            FROM documents doc
            LEFT JOIN court_sessions s ON s.source_document_id = doc.id
            JOIN cases c ON doc.case_id = c.id
            ORDER BY doc.uploaded_at
        """).fetchall()
    conn.close()

    lines = []
    for r in rows:
        d = dict(r)
        dt = (d.get("uploaded_at") or "")[:10]
        doc = d.get("document_type", "")
        sess = d.get("session_time") or ""
        case = d.get("case_number", "")
        if case:
            lines.append(f"{dt}  {case}  {doc}  {sess}")
        else:
            lines.append(f"{dt}  {doc}  {sess}")
    return "\n".join(lines) if lines else "暂无记录"


def calendar_html(archive_dir: str | Path, output_path: str | Path | None = None) -> Path:
    """生成期限日历 HTML 页面"""
    archive_dir = Path(archive_dir).expanduser()
    init_db(archive_dir)
    conn = get_connection(archive_dir)
    rows = conn.execute("""
        SELECT c.case_number, c.cause_of_action, d.deadline_type, d.due_date, d.is_completed
        FROM deadlines d
        JOIN cases c ON d.case_id = c.id
        ORDER BY d.due_date
    """).fetchall()
    conn.close()

    out = output_path or archive_dir / "期限日历.html"
    out = Path(out)
    rows_data = [dict(r) for r in rows]
    html = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Dockit 期限日历</title>
<style>table{{border-collapse:collapse}} th,td{{border:1px solid #ccc;padding:8px}} th{{background:#eee}}</style>
</head><body>
<h2>关键期限</h2>
<table><tr><th>案号</th><th>案由</th><th>期限类型</th><th>到期日</th><th>状态</th></tr>
"""
    for r in rows_data:
        status = "已完成" if r["is_completed"] else "待办"
        html += f"<tr><td>{r['case_number']}</td><td>{r['cause_of_action'] or ''}</td><td>{r['deadline_type']}</td><td>{r['due_date']}</td><td>{status}</td></tr>"
    html += "</table></body></html>"
    out.write_text(html, encoding="utf-8")
    logger.info("已生成: %s", out)
    return out
