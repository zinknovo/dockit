"""LLM classify - same logic as client"""

import json
import os
import re

from openai import OpenAI

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


def _parse_json(s: str) -> dict:
    s = s.strip()
    if s.startswith("```"):
        lines = s.split("\n")
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        s = "\n".join(lines)
    return json.loads(s)


def _first(arr):
    return str(arr[0]).strip() if arr and len(arr) > 0 else None


def classify(document_text: str) -> dict:
    api_key = os.environ.get("LLM_API_KEY")
    if not api_key:
        raise ValueError("LLM_API_KEY not set")

    client = OpenAI(api_key=api_key, base_url=os.environ.get("LLM_BASE_URL", "https://api.deepseek.com"))
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
    raw = _parse_json(resp.choices[0].message.content)

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

    return {
        "document_type": raw.get("document_type") or "其他",
        "case_number": raw.get("case_number"),
        "court_name": raw.get("court_name"),
        "plaintiff": _first(plaintiffs),
        "defendant": _first(defendants),
        "document_date": raw.get("document_date"),
        "cause_of_action": raw.get("cause_of_action"),
        "hearing_time": raw.get("hearing_time"),
        "hearing_location": raw.get("hearing_location"),
        "evidence_deadline": raw.get("evidence_deadline"),
        "defense_deadline": raw.get("defense_deadline"),
        "appeal_deadline": raw.get("appeal_deadline"),
        "judge": raw.get("judge"),
        "panel_members": raw.get("panel_members"),
        "judgment_result": raw.get("judgment_result"),
        "judgment_amount": raw.get("judgment_amount"),
        "raw_json": raw,
    }
