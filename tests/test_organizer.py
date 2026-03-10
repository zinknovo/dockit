"""Tests for organizer - 路径计算与文件移动"""

from pathlib import Path

import pytest

from dockit.db.models import DocumentInfo
from dockit.core.organizer import (
    compute_target_path,
    move_file,
    move_to_unidentified,
    _sanitize,
    _ensure_unique_path,
)


def test_sanitize_replaces_invalid_chars():
    assert _sanitize("a/b:c") == "a_b_c"
    assert _sanitize("x*?<>|") == "x_____"


def test_sanitize_truncates_when_max_len():
    assert _sanitize("hello world", max_len=5) == "hello"


def test_ensure_unique_path_adds_number(tmp_path):
    (tmp_path / "a.pdf").touch()
    dest = _ensure_unique_path(tmp_path / "a.pdf")
    assert dest.name == "a(2).pdf"


def test_ensure_unique_path_increments_when_2_exists(tmp_path):
    (tmp_path / "a.pdf").touch()
    (tmp_path / "a(2).pdf").touch()
    dest = _ensure_unique_path(tmp_path / "a.pdf")
    assert dest.name == "a(3).pdf"


def test_move_file_direct_move(tmp_path):
    src = tmp_path / "b.pdf"
    src.write_bytes(b"data")
    dest = tmp_path / "out" / "b.pdf"
    move_file(src, dest, copy_then_delete=False)
    assert not src.exists()
    assert dest.exists()


def test_compute_target_path_returns_none_when_no_case_number(tmp_path):
    doc = DocumentInfo(
        document_type="传票",
        case_number=None,
        court_name="朝阳法院",
        plaintiff="张三",
        defendant="某某公司",
        document_date="2024-03-01",
        cause_of_action="合同纠纷",
        hearing_time=None,
        hearing_location=None,
        evidence_deadline=None,
        defense_deadline=None,
        appeal_deadline=None,
        judge=None,
        panel_members=None,
        judgment_result=None,
        judgment_amount=None,
        raw_json={},
    )
    config = {"max_party_name_length": 10, "folder_template": "{case_number}_{cause_of_action}", "filename_template": "{document_type}_{plaintiff}v{defendant}_{date}"}
    assert compute_target_path(config, doc, tmp_path) is None


def test_compute_target_path_returns_path(tmp_path):
    doc = DocumentInfo(
        document_type="传票",
        case_number="（2024）京0105民初12345号",
        court_name="朝阳法院",
        plaintiff="张三",
        defendant="北京某某科技",
        document_date="2024-03-01",
        cause_of_action="合同纠纷",
        hearing_time=None,
        hearing_location=None,
        evidence_deadline=None,
        defense_deadline=None,
        appeal_deadline=None,
        judge=None,
        panel_members=None,
        judgment_result=None,
        judgment_amount=None,
        raw_json={},
    )
    config = {"max_party_name_length": 10, "folder_template": "{case_number}_{cause_of_action}", "filename_template": "{document_type}_{plaintiff}v{defendant}_{date}"}
    out = compute_target_path(config, doc, tmp_path)
    assert out is not None
    assert "12345" in str(out)
    assert out.suffix == ".pdf"


def test_move_file_copy_then_delete(tmp_path):
    src = tmp_path / "a.pdf"
    src.write_bytes(b"content")
    dest = tmp_path / "sub" / "a.pdf"
    move_file(src, dest, copy_then_delete=True)
    assert not src.exists()
    assert dest.exists()
    assert dest.read_bytes() == b"content"


def test_move_to_unidentified(tmp_path):
    src = tmp_path / "x.pdf"
    src.write_bytes(b"x")
    archive = tmp_path / "archive"
    result = move_to_unidentified(archive, src, "reason")
    assert not src.exists()
    assert (archive / "_未识别" / "x.pdf").exists()
    assert result == archive / "_未识别" / "x.pdf"
