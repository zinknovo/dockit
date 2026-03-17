"""classifier 模块测试"""

from unittest.mock import patch, MagicMock

import pytest

from dockit.core.classifier import classify
from dockit.db.models import DocumentInfo


def test_classify_via_backend_api():
    """配置 api_base_url + api_token 时调用后端 API"""
    config = {
        "llm": {
            "api_base_url": "http://localhost:8000",
            "api_token": "test-token",
        }
    }
    resp = {
        "document_type": "传票",
        "case_number": "（2024）京0105民初1号",
        "court_name": "朝阳法院",
        "plaintiff": "张三",
        "defendant": "李四",
        "cause_of_action": "合同纠纷",
    }
    mock_resp = MagicMock()
    mock_resp.read.return_value = __import__("json").dumps(resp).encode("utf-8")
    mock_resp.__enter__ = MagicMock(return_value=mock_resp)
    mock_resp.__exit__ = MagicMock(return_value=False)

    with patch("dockit.core.classifier.urlopen", return_value=mock_resp):
        result = classify(config, "传票 原告张三 被告李四" * 10)
    assert isinstance(result, DocumentInfo)
    assert result.document_type == "传票"
    assert result.case_number == "（2024）京0105民初1号"
    assert result.plaintiff == "张三"
    assert result.defendant == "李四"


def test_classify_requires_backend_config():
    """未配置 api_base_url / api_token 时抛错"""
    config = {"llm": {}}
    with patch("os.environ", {}):
        with pytest.raises(ValueError, match="api_base_url|api_token"):
            classify(config, "xxx" * 50)
