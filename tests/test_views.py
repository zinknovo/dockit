"按""views 模块测试"""

from pathlib import Path

import pytest

from dockit.db.db import init_db, get_connection
from dockit.ui.views import calendar_text, timeline_text, calendar_html


@pytest.fixture
def archive_with_deadlines(tmp_path):
    init_db(tmp_path)
    conn = get_connection(tmp_path)
    conn.execute(
        "INSERT INTO cases (case_number, cause_of_action) VALUES (?,?)",
        ("（2024）京0105民初1号", "合同纠纷"),
    )
    cid = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
    conn.execute(
        "INSERT INTO deadlines (case_id, deadline_type, due_date, is_completed) VALUES (?,?,?,?)",
        (cid, "举证期限", "2024-03-20", 0),
    )
    conn.commit()
    conn.close()
    return tmp_path


def test_calendar_text_empty(tmp_path):
    init_db(tmp_path)
    s = calendar_text(tmp_path)
    assert "案号" in s
    assert "期限" in s


def test_calendar_text_with_data(archive_with_deadlines):
    s = calendar_text(archive_with_deadlines)
    assert "案号" in s
    assert "举证" in s
    assert "2024-03-20" in s


def test_timeline_text_empty(tmp_path):
    init_db(tmp_path)
    s = timeline_text(tmp_path)
    assert "暂无记录" in s


def test_timeline_text_with_case_number_filter(archive_with_deadlines):
    """timeline_text(case_number=...) 过滤案号"""
    conn = get_connection(archive_with_deadlines)
    conn.execute("INSERT INTO cases (case_number) VALUES (?)", ("（2024）京0105民初2号",))
    cid2 = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
    conn.execute(
        "INSERT INTO documents (case_id, document_type, original_filename, uploaded_at) VALUES (?,?,?,?)",
        (cid2, "判决书", "j.pdf", "2024-03-10 12:00:00"),
    )
    conn.commit()
    conn.close()
    s = timeline_text(archive_with_deadlines, case_number="（2024）京0105民初2号")
    assert "判决书" in s


def test_timeline_text_with_data(archive_with_deadlines):
    init_db(archive_with_deadlines)
    conn = get_connection(archive_with_deadlines)
    conn.execute(
        "INSERT INTO cases (case_number) VALUES (?)", ("（2024）京0105民初2号",)
    )
    cid = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
    conn.execute(
        "INSERT INTO documents (case_id, document_type, original_filename) VALUES (?,?,?)",
        (cid, "传票", "x.pdf"),
    )
    conn.commit()
    conn.close()
    s = timeline_text(archive_with_deadlines)
    assert "传票" in s or "案号" in s


def test_calendar_html(archive_with_deadlines):
    out = archive_with_deadlines / "cal.html"
    result = calendar_html(archive_with_deadlines, out)
    assert result == out
    assert out.exists()
    html = out.read_text(encoding="utf-8")
    assert "<table" in html
    assert "举证" in html
