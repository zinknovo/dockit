"""export 模块测试"""

import tempfile
from pathlib import Path

import pytest

from dockit.db.db import init_db, get_connection
from dockit.tools.export import export_court_sessions, export_deadlines


@pytest.fixture
def archive_with_data(tmp_path):
    init_db(tmp_path)
    conn = get_connection(tmp_path)
    conn.execute(
        "INSERT INTO cases (case_number, cause_of_action, court_name, plaintiff, defendant) VALUES (?,?,?,?,?)",
        ("（2024）京0105民初1号", "合同纠纷", "朝阳法院", "张三", "李四"),
    )
    cid = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
    conn.execute(
        "INSERT INTO court_sessions (case_id, session_time, location, judge) VALUES (?,?,?,?)",
        (cid, "2024-03-15 09:00", "三法庭", "王法官"),
    )
    conn.execute(
        "INSERT INTO deadlines (case_id, deadline_type, due_date, is_completed) VALUES (?,?,?,?)",
        (cid, "举证期限", "2024-03-20", 0),
    )
    conn.commit()
    conn.close()
    return tmp_path


def test_export_court_sessions(archive_with_data):
    out = archive_with_data / "out.xlsx"
    result = export_court_sessions(archive_with_data, out)
    assert result == out
    assert out.exists()
    assert out.stat().st_size > 0


def test_export_deadlines(archive_with_data):
    out = archive_with_data / "d.xlsx"
    result = export_deadlines(archive_with_data, out)
    assert result == out
    assert out.exists()
    assert out.stat().st_size > 0


def test_export_court_sessions_default_path(archive_with_data):
    result = export_court_sessions(archive_with_data)
    assert (archive_with_data / "开庭记录表.xlsx").exists()


def test_export_deadlines_empty(tmp_path):
    init_db(tmp_path)
    out = tmp_path / "d.xlsx"
    export_deadlines(tmp_path, out)
    assert out.exists()


def test_export_deadlines_default_path(archive_with_data):
    """export_deadlines 不传 output_path 时默认归档目录/关键期限.xlsx"""
    result = export_deadlines(archive_with_data)
    assert (archive_with_data / "关键期限.xlsx").exists()
    assert result == archive_with_data / "关键期限.xlsx"


def test_export_deadlines_with_completed(archive_with_data):
    """导出时 is_completed=1 显示为「是」"""
    conn = get_connection(archive_with_data)
    cid = conn.execute("SELECT id FROM cases LIMIT 1").fetchone()[0]
    conn.execute(
        "INSERT INTO deadlines (case_id, deadline_type, due_date, is_completed) VALUES (?,?,?,?)",
        (cid, "上诉期限", "2024-04-01", 1),
    )
    conn.commit()
    conn.close()
    out = archive_with_data / "d2.xlsx"
    export_deadlines(archive_with_data, out)
    assert out.exists()
