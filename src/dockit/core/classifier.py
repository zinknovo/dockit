# classifier.py - 文书分类（后端 API 或直连 LLM）
"""支持官方后端 /api/classify 或直连 LLM（无登录时用 OPENAI_API_KEY 测试）"""

import json
import logging
import os
import re
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from ..db.models import DocumentInfo

logger = logging.getLogger(__name__)

# 直连 LLM 用（无 api_token 时的回退）
CASE_NUMBER_PATTERN = re.compile(r"（\d{4}）[\u4e00-\u9fa5]+\d+[\u4e00-\u9fa5]+\d+号")
SYSTEM_PROMPT = """你是一个法律文书解析助手。请从以下文书中提取结构化信息，仅返回 JSON，不要包含任何其他文字或 markdown 标记。

必须提取的字段：
- document_type: 文书类型（传票/举证通知书/应诉通知书/民事裁定书/民事判决书/行政判决书/起诉状/答辩状/调解书/执行通知书/保全裁定书/其他）
- case_number: 案号（如"（2024）京0105民初12345号"）
- court_name: 法院名称
- plaintiff: 原告（数组）
- defendant: 被告（数组）
- document_date: 文书日期（YYYY-MM-DD）
- cause_of_action: 案由
- hearing_time: 开庭时间（YYYY-MM-DD HH:mm）
- hearing_location: 开庭地点
- evidence_deadline: 举证期限
- defense_deadline: 答辩期限
- appeal_deadline: 上诉期限
- judge: 审判长
- panel_members: 合议庭成员（数组）
- judgment_result: 判决/裁定摘要
- judgment_amount: 判决金额
字段不存在则 null。"""

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


def _classify_via_direct_llm(document_text: str) -> dict:
    """无登录时直连 LLM（需 .env 中 OPENAI_API_KEY 或 LLM_API_KEY）"""
    api_key = os.environ.get("OPENAI_API_KEY") or os.environ.get("LLM_API_KEY")
    if not api_key:
        raise ValueError("未配置 api_token，且无 OPENAI_API_KEY/LLM_API_KEY，无法分类。请登录或设置 .env")
    from openai import OpenAI
    client = OpenAI(
        api_key=api_key,
        base_url=os.environ.get("LLM_BASE_URL", "https://api.deepseek.com"),
    )
    model = os.environ.get("LLM_MODEL", "deepseek-chat")
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": document_text},
        ],
        temperature=0.1,
        response_format={"type": "json_object"},
    )
    raw = json.loads(resp.choices[0].message.content or "{}")
    if not (raw.get("case_number") or str(raw.get("case_number", "")).strip()):
        m = CASE_NUMBER_PATTERN.search(document_text)
        if m:
            raw["case_number"] = m.group(0)
    plaintiffs = raw.get("plaintiff")
    defendants = raw.get("defendant")
    if isinstance(plaintiffs, str):
        plaintiffs = [plaintiffs]
    if isinstance(defendants, str):
        defendants = [defendants]
    raw["plaintiff"] = _extract_first(plaintiffs) if plaintiffs else None
    raw["defendant"] = _extract_first(defendants) if defendants else None
    return raw


def classify(config: dict, document_text: str) -> DocumentInfo:
    """
    提取文书结构化信息。优先走后端 API；无 token 时用 OPENAI_API_KEY 直连 LLM（测试用）。

    Args:
        config: 配置字典
        document_text: 文书全文

    Returns:
        DocumentInfo 对象
    """
    llm_config = config.get("llm") or {}
    api_base_url = llm_config.get("api_base_url") or os.environ.get("DOCKIT_API_BASE_URL")
    api_token = (llm_config.get("api_token") or "").strip() or os.environ.get("DOCKIT_API_TOKEN")

    if api_base_url and api_token:
        raw = _call_backend_api(api_base_url.rstrip("/"), api_token, document_text)
    else:
        raw = _classify_via_direct_llm(document_text)
    return _raw_to_document_info(raw)
