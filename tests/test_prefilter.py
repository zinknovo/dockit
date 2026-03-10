"""预筛模块测试"""

import json
import tempfile
from pathlib import Path
from unittest.mock import patch

import pytest

from dockit.core.prefilter import (
    CASE_NUMBER_PATTERN,
    LEGAL_KEYWORDS,
    is_likely_legal,
    load_prefilter_stats,
    log_prefilter_decision,
)


def test_legal_keywords_include_law_regulation():
    assert "规定" in LEGAL_KEYWORDS
    assert "条例" in LEGAL_KEYWORDS
    assert "法律" in LEGAL_KEYWORDS
    assert "依据" in LEGAL_KEYWORDS or "根据" in LEGAL_KEYWORDS


def test_legal_keywords_exist():
    assert len(LEGAL_KEYWORDS) >= 10
    assert "传票" in LEGAL_KEYWORDS
    assert "法院" in LEGAL_KEYWORDS
    assert "案号" in LEGAL_KEYWORDS


def test_case_number_pattern():
    assert CASE_NUMBER_PATTERN.search("（2024）京0105民初12345号")
    assert CASE_NUMBER_PATTERN.search("xxx（2023）沪01民终999号yyy")
    assert not CASE_NUMBER_PATTERN.search("（2024）京0105")
    assert not CASE_NUMBER_PATTERN.search("2024年")


def test_is_likely_legal_empty():
    ok, reason = is_likely_legal("")
    assert not ok
    assert "short" in reason or "too" in reason

    ok, reason = is_likely_legal("a" * 20)
    assert not ok


def test_is_likely_legal_case_number():
    ok, reason = is_likely_legal("开庭传票。案号：（2024）京0105民初12345号。北京市朝阳区人民法院。")
    assert ok
    assert "case_number" in reason


def test_is_likely_legal_keywords():
    ok, reason = is_likely_legal("原告张三，被告李四。案由：合同纠纷。法院：北京市朝阳区人民法院。")
    assert ok
    assert "keyword" in reason


def test_is_likely_legal_too_few_keywords():
    ok, reason = is_likely_legal("这是一份普通合同，甲方乙方，签字盖章。")
    assert not ok


def test_is_likely_legal_receipt():
    ok, _ = is_likely_legal("""
        购物小票
        商品：xxx 金额：100元
        谢谢惠顾
    """)
    assert not ok


def test_is_likely_legal_law_regulation():
    ok, reason = is_likely_legal(
        "依据《中华人民共和国民法典》第九条规定，当事人订立合同应当遵循自愿原则。"
    )
    assert ok
    assert "keyword" in reason or "case_number" in reason


def test_is_likely_legal_regulation_ordinance():
    ok, _ = is_likely_legal(
        "《北京市物业管理条例》第五十条规定，业主大会决定事项应当经专有部分占建筑物总面积过半数的业主同意。"
    )
    assert ok


def test_log_and_load_stats():
    with tempfile.TemporaryDirectory() as d:
        log_prefilter_decision(d, "a.pdf", "passed", "case_number_match", 500)
        log_prefilter_decision(d, "b.pdf", "rejected", "keywords_0", 100)
        stats = load_prefilter_stats(d)
        assert stats["total"] == 2
        assert stats["passed"] == 1
        assert stats["rejected"] == 1
        assert "case_number_match" in stats["by_reason"]
        assert stats["by_reason"]["case_number_match"] == 1


def test_log_prefilter_decision_handles_oserror(caplog):
    with tempfile.TemporaryDirectory() as d:
        with patch("builtins.open", side_effect=OSError("permission denied")):
            log_prefilter_decision(d, "a.pdf", "passed", "test", 100)
    assert "预筛日志写入失败" in caplog.text or "permission" in caplog.text.lower()


def test_load_prefilter_stats_malformed_line():
    with tempfile.TemporaryDirectory() as d:
        log = Path(d) / "prefilter_log.jsonl"
        log.write_text('{"valid": true}\nnot json\n{"decision":"passed","reason":"x"}\n', encoding="utf-8")
        stats = load_prefilter_stats(d)
        assert stats["total"] == 2
        assert stats["passed"] >= 1


def test_load_prefilter_stats_empty_or_nonexistent():
    with tempfile.TemporaryDirectory() as d:
        stats = load_prefilter_stats(d)
        assert stats["total"] == 0
        assert stats["passed"] == 0
        assert stats["rejected"] == 0
        assert stats["by_reason"] == {}
