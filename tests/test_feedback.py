"""feedback 模块测试"""

from pathlib import Path

import pytest

from dockit.tools.feedback import save_correction, load_corrections, load_few_shot_examples


def test_save_and_load_corrections(tmp_path):
    save_correction(
        tmp_path,
        "x.pdf",
        {"document_type": "传票", "case_number": "xxx"},
        {"document_type": "传票", "case_number": "（2024）京0105民初1号"},
        ["case_number"],
        "原告张三被告李四" * 50,
    )
    recs = load_corrections(tmp_path)
    assert len(recs) == 1
    assert recs[0]["original_filename"] == "x.pdf"
    assert recs[0]["correction_fields"] == ["case_number"]


def test_load_corrections_empty(tmp_path):
    assert load_corrections(tmp_path) == []


def test_load_few_shot_examples_empty(tmp_path):
    assert load_few_shot_examples(tmp_path, "xxx") == ""


def test_load_corrections_skips_malformed(tmp_path):
    (tmp_path / "corrections.jsonl").write_text('{"valid":1}\nnot json\n', encoding="utf-8")
    recs = load_corrections(tmp_path)
    assert len(recs) == 1


def test_load_few_shot_examples_with_data(tmp_path):
    save_correction(
        tmp_path,
        "a.pdf",
        {},
        {"document_type": "传票", "case_number": "（2024）京0105民初1号"},
        ["case_number"],
        "传票 原告" * 100,
    )
    s = load_few_shot_examples(tmp_path, "yyy", max_examples=2)
    assert "示例" in s
    assert "传票" in s


def test_load_few_shot_examples_respects_max_tokens(tmp_path):
    """max_tokens 较小时只返回部分示例"""
    for i in range(5):
        save_correction(
            tmp_path,
            f"x{i}.pdf",
            {},
            {"document_type": "传票", "case_number": f"（2024）京0105民初{i}号"},
            [],
            "x" * 500,
        )
    s = load_few_shot_examples(tmp_path, "yyy", max_examples=10, max_tokens=50)
    assert len(s) <= 200
