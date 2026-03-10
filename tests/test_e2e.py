"""E2E 测试：假传票 PDF 完整流程"""

import tempfile
from pathlib import Path
from unittest.mock import patch

import pytest

from dockit.core.extractor import extract_text
from dockit.core.organizer import compute_target_path
from dockit.db.models import DocumentInfo


def test_fake_summons_extract_and_path():
    """假传票：提取文本 + 计算目标路径"""
    # 使用项目内生成的测试 PDF（若存在）
    pdf = Path(__file__).resolve().parent / "fixtures" / "test_传票.pdf"
    if not pdf.exists():
        pytest.skip("运行 scripts/gen_test_pdf.py 先生成 test_传票.pdf")

    text, valid = extract_text(pdf)
    assert valid, f"文本过短或无效，len={len(text)}"
    assert "12345" in text or "传票" in text or "案号" in text

    # 模拟 LLM 返回
    doc = DocumentInfo(
        document_type="传票",
        case_number="（2024）京0105民初12345号",
        court_name="朝阳法院",
        plaintiff="张三",
        defendant="北京某某科技有限公司",
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
        raw_json={},
    )
    config = {
        "max_party_name_length": 10,
        "folder_template": "{case_number}_{cause_of_action}",
        "filename_template": "{document_type}_{plaintiff}v{defendant}_{date}",
    }
    target = compute_target_path(config, doc, Path(tempfile.mkdtemp()))
    assert target is not None
    assert "12345" in str(target)
