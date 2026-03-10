# prefilter.py - 法律文书启发式预筛
"""提取文本后、调用 LLM 前，用关键词判断是否可能是法律文书，减少 API 消耗"""

import json
import logging
import re
from datetime import datetime, timezone
from pathlib import Path

logger = logging.getLogger(__name__)

# 至少匹配其中几个才认为是「可能法律文书」
MIN_KEYWORD_MATCH = 2

# 法律文书常见关键词（律师常见抬头、文书类型、机构等）
LEGAL_KEYWORDS = frozenset({
    "传票", "法院", "原告", "被告", "案号", "民事", "裁定", "判决",
    "举证", "应诉", "起诉", "答辩", "开庭", "审判", "送达",
    "诉讼", "案由", "管辖权", "上诉", "再审",
    "调解", "执行", "保全", "通知书", "裁定书", "判决书",
    "检察院", "公诉", "刑事", "仲裁", "仲裁委", "调解书", "起诉状",
    "规定", "条例", "施行", "条款", "依据", "根据", "法律", "司法解释",
    "民法典", "刑法", "诉讼法", "合同法", "公司法", "第条",
    "管辖权异议", "强制执行", "立案", "受理",
    "律师函", "法律意见书", "委托书", "授权委托书", "证据清单", "质证意见",
    "代理词", "辩护词", "申诉书", "再审申请书", "执行申请书", "支付令",
    "公示催告", "诉前保全", "财产保全", "先予执行", "法庭", "审判员", "书记员",
    "鉴定意见", "评估报告", "公证书", "公证处", "律师事务所", "律师",
    "谅解书", "和解协议", "风险代理", "诉讼费", "受理费",
})

# 案号正则，匹配则强通过
CASE_NUMBER_PATTERN = re.compile(r"（\d{4}）[\u4e00-\u9fa5]+\d+[\u4e00-\u9fa5]+\d+号")


def is_likely_legal(text: str) -> tuple[bool, str]:
    """
    启发式判断文本是否可能是中国法律文书。

    Returns:
        (是否通过, 原因说明)
    """
    if not text or len(text.strip()) < 30:
        return False, "text_too_short"

    t = text.strip()
    matched = sum(1 for kw in LEGAL_KEYWORDS if kw in t)
    if CASE_NUMBER_PATTERN.search(t):
        return True, "case_number_match"
    if matched >= MIN_KEYWORD_MATCH:
        return True, f"keywords_{matched}"
    return False, f"keywords_{matched}"


def log_prefilter_decision(
    archive_dir: str | Path,
    file_path: str | Path,
    decision: str,  # "passed" | "rejected"
    reason: str,
    text_length: int = 0,
) -> None:
    """写入预筛决策日志，便于监控与调参"""
    path = Path(archive_dir).expanduser()
    path.mkdir(parents=True, exist_ok=True)
    log_file = path / "prefilter_log.jsonl"
    entry = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "file": Path(file_path).name,
        "filepath": str(file_path),
        "decision": decision,
        "reason": reason,
        "text_len": text_length,
    }
    try:
        with open(log_file, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except OSError as e:
        logger.warning("预筛日志写入失败: %s", e)


def load_prefilter_stats(archive_dir: str | Path) -> dict:
    """从 prefilter_log.jsonl 统计预筛结果"""
    log_file = Path(archive_dir).expanduser() / "prefilter_log.jsonl"
    if not log_file.exists():
        return {"total": 0, "passed": 0, "rejected": 0, "by_reason": {}}

    total = passed = rejected = 0
    by_reason: dict[str, int] = {}
    with open(log_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                e = json.loads(line)
                total += 1
                if e.get("decision") == "passed":
                    passed += 1
                else:
                    rejected += 1
                r = e.get("reason", "unknown")
                by_reason[r] = by_reason.get(r, 0) + 1
            except json.JSONDecodeError:
                continue
    return {"total": total, "passed": passed, "rejected": rejected, "by_reason": by_reason}
