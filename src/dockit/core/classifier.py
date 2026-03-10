# classifier.py - 文书分类（仅走后端 API）
"""调用官方后端 /api/classify 识别法律文书，不支持直连 LLM"""

import json
import logging
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from ..db.models import DocumentInfo

logger = logging.getLogger(__name__)

def _extract_first(arr: list | None) -> str | None:
    """从数组取第一个元素"""
    if arr and len(arr) > 0:
        return str(arr[0]).strip() or None
    return None


def _call_backend_api(api_base_url: str, api_token: str, document_text: str) -> dict:
    """调用后端 /api/classify，返回 JSON dict"""
    base = api_base_url.rstrip("/")
    url = f"{base}/api/classify"
    body = json.dumps({"text": document_text}).encode("utf-8")
    req = Request(url, data=body, method="POST", headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_token}",
    })
    try:
        with urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode("utf-8"))
    except HTTPError as e:
        body = e.read().decode("utf-8", errors="replace") if e.fp else ""
        raise ValueError(f"API 请求失败 {e.code}: {body}")
    except URLError as e:
        raise ValueError(f"无法连接 API: {e.reason}")


def _raw_to_document_info(raw: dict) -> DocumentInfo:
    """将 API/LLM 返回的 dict 转为 DocumentInfo"""
    plaintiffs = raw.get("plaintiff")
    defendants = raw.get("defendant")
    if isinstance(plaintiffs, (list, tuple)):
        plaintiffs = _extract_first(list(plaintiffs))
    elif plaintiffs is not None:
        plaintiffs = str(plaintiffs).strip() or None
    if isinstance(defendants, (list, tuple)):
        defendants = _extract_first(list(defendants))
    elif defendants is not None:
        defendants = str(defendants).strip() or None

    pm = raw.get("panel_members")
    if isinstance(pm, str):
        pm = [pm] if pm.strip() else None
    elif pm is not None and not isinstance(pm, list):
        pm = None

    return DocumentInfo(
        document_type=raw.get("document_type") or "其他",
        case_number=raw.get("case_number"),
        court_name=raw.get("court_name"),
        plaintiff=plaintiffs,
        defendant=defendants,
        document_date=raw.get("document_date"),
        cause_of_action=raw.get("cause_of_action"),
        hearing_time=raw.get("hearing_time"),
        hearing_location=raw.get("hearing_location"),
        evidence_deadline=raw.get("evidence_deadline"),
        defense_deadline=raw.get("defense_deadline"),
        appeal_deadline=raw.get("appeal_deadline"),
        judge=raw.get("judge"),
        panel_members=pm,
        judgment_result=raw.get("judgment_result"),
        judgment_amount=raw.get("judgment_amount"),
        raw_json=raw,
    )


def classify(config: dict, document_text: str) -> DocumentInfo:
    """
    调用官方后端 /api/classify 提取文书结构化信息。
    必须配置 api_base_url 与 api_token，不支持直连 LLM。

    Args:
        config: 配置字典，llm 下需有 api_base_url、api_token（或环境变量 DOCKIT_API_BASE_URL、DOCKIT_API_TOKEN）
        document_text: 文书全文

    Returns:
        DocumentInfo 对象
    """
    llm_config = config.get("llm") or {}
    api_base_url = llm_config.get("api_base_url") or os.environ.get("DOCKIT_API_BASE_URL")
    api_token = llm_config.get("api_token") or os.environ.get("DOCKIT_API_TOKEN")

    if not api_base_url or not api_token:
        raise ValueError(
            "请先登录并配置 api_base_url 与 api_token（config.yaml 或环境变量 DOCKIT_API_BASE_URL、DOCKIT_API_TOKEN）"
        )

    raw = _call_backend_api(api_base_url.strip(), api_token, document_text)
    return _raw_to_document_info(raw)
