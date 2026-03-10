"""Tests for db module"""

from pathlib import Path
from unittest.mock import patch

import pytest

from dockit.db.db import (
    init_db,
    get_connection,
    upsert_case,
    insert_document,
    insert_court_session,
    insert_deadline,
    sync_from_archive,
    list_deadlines,
    document_exists_by_hash,
    file_hash,
)
from dockit.db.models import DocumentInfo


def test_init_db(tmp_path):
    init_db(tmp_path)
    db_file = tmp_path / "dockit.db"
    assert db_file.exists()


def test_upsert_case(tmp_path):
    init_db(tmp_path)
    conn = get_connection(tmp_path)
    try:
        cid = upsert_case(conn, "（2024）京0105民初12345号", "合同纠纷", "朝阳法院", "张三", "某某公司", "xxx")
        assert cid > 0
        cid2 = upsert_case(conn, "（2024）京0105民初12345号", "劳动纠纷", None, None, None, "yyy")
        assert cid2 == cid
    finally:
        conn.close()


def test_sync_from_archive(tmp_path):
    init_db(tmp_path)
    doc = DocumentInfo(
        document_type="传票",
        case_number="（2024）京0105民初12345号",
        court_name="朝阳法院",
        plaintiff="张三",
        defendant="某某公司",
        document_date="2024-03-01",
        cause_of_action="合同纠纷",
        hearing_time="2024-04-15 09:30",
        hearing_location="第三法庭",
        evidence_deadline="2024-04-01",
        defense_deadline=None,
        appeal_deadline=None,
        judge="王法官",
        panel_members=None,
        judgment_result=None,
        judgment_amount=None,
        raw_json={"document_type": "传票", "case_number": "（2024）京0105民初12345号"},
    )
    sync_from_archive(tmp_path, doc, "test.pdf", tmp_path / "案卷" / "传票.pdf")

    conn = get_connection(tmp_path)
    cases = conn.execute("SELECT * FROM cases").fetchall()
    docs = conn.execute("SELECT * FROM documents").fetchall()
    sessions = conn.execute("SELECT * FROM court_sessions").fetchall()
    deadlines = conn.execute("SELECT * FROM deadlines").fetchall()
    conn.close()

    assert len(cases) == 1
    assert cases[0]["case_number"] == "（2024）京0105民初12345号"
    assert len(docs) == 1
    assert len(sessions) == 1
    assert len(deadlines) == 1
    assert deadlines[0]["deadline_type"] == "举证期限"


def test_list_deadlines_empty_when_no_db(tmp_path):
    assert list_deadlines(tmp_path) == []


def test_list_deadlines_with_data(tmp_path):
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
    rows = list_deadlines(tmp_path)
    assert len(rows) == 1
    assert rows[0]["case_number"] == "（2024）京0105民初1号"
    assert rows[0]["deadline_type"] == "举证期限"
    assert rows[0]["is_completed"] is False


def test_document_exists_by_hash(tmp_path):
    init_db(tmp_path)
    pdf = tmp_path / "test.pdf"
    pdf.write_bytes(b"%PDF-1.4 x")
    h = file_hash(pdf)
    assert document_exists_by_hash(tmp_path, "nonexistent_hash") is False
    sync_from_archive(
        tmp_path,
        DocumentInfo(
            document_type="传票",
            case_number="（2024）京0105民初1号",
            court_name="朝阳法院",
            plaintiff="",
            defendant="",
            document_date="",
            cause_of_action="合同",
            hearing_time="",
            hearing_location="",
            evidence_deadline=None,
            defense_deadline=None,
            appeal_deadline=None,
            judge=None,
            panel_members=None,
            judgment_result=None,
            judgment_amount=None,
            raw_json={},
        ),
        "test.pdf",
        pdf,
    )
    assert document_exists_by_hash(tmp_path, h) is True


def test_file_hash(tmp_path):
    f = tmp_path / "x.bin"
    f.write_bytes(b"hello")
    h = file_hash(f)
    assert len(h) == 64
    assert h == "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"


def test_sync_from_archive_rollback_on_error(tmp_path):
    """sync_from_archive 在异常时 rollback"""
    init_db(tmp_path)
    doc = DocumentInfo(
        document_type="传票",
        case_number="（2024）京0105民初1号",
        court_name="朝阳法院",
        plaintiff="",
        defendant="",
        document_date="",
        cause_of_action="合同",
        hearing_time="",
        hearing_location="",
        evidence_deadline=None,
        defense_deadline=None,
        appeal_deadline=None,
        judge=None,
        panel_members=None,
        judgment_result=None,
        judgment_amount=None,
        raw_json={},
    )
    pdf = tmp_path / "a.pdf"
    pdf.write_bytes(b"%PDF")
    with patch("dockit.db.db.upsert_case", side_effect=RuntimeError("test")):
        try:
            sync_from_archive(tmp_path, doc, "a.pdf", pdf)
        except RuntimeError:
            pass
    conn = get_connection(tmp_path)
    count = conn.execute("SELECT COUNT(*) FROM cases").fetchone()[0]
    conn.close()
    assert count == 0
