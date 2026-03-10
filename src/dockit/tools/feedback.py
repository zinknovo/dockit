# feedback.py - 修正记录读写与 few-shot 选择
"""修正记录追加、读取及 few-shot 示例选择"""

import json
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

# 修正记录文件名
CORRECTIONS_FILE = "corrections.jsonl"

# few-shot 相关限制
MAX_PREVIEW_CHARS = 500
APPROX_CHARS_PER_TOKEN = 2  # 粗略估算


def _corrections_path(archive_dir: Path) -> Path:
    """修正记录文件路径"""
    return Path(archive_dir).expanduser() / CORRECTIONS_FILE


def save_correction(
    archive_dir: str | Path,
    original_filename: str,
    llm_output: dict,
    user_correction: dict,
    correction_fields: list[str],
    pdf_text_preview: str,
) -> None:
    """
    追加一条修正记录到 corrections.jsonl。

    Args:
        archive_dir: 归档根目录
        original_filename: 原始文件名
        llm_output: LLM 原始识别结果
        user_correction: 用户修正后的结果
        correction_fields: 被修正的字段列表
        pdf_text_preview: 文书文本前 500 字
    """
    from datetime import datetime

    path = _corrections_path(archive_dir)
    path.parent.mkdir(parents=True, exist_ok=True)

    record = {
        "timestamp": datetime.now().isoformat(timespec="seconds"),
        "original_filename": original_filename,
        "llm_output": llm_output,
        "user_correction": user_correction,
        "correction_fields": correction_fields,
        "pdf_text_preview": pdf_text_preview[:MAX_PREVIEW_CHARS],
    }
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")
    logger.info("已记录修正: %s", correction_fields)


def load_corrections(archive_dir: Path) -> list[dict]:
    """加载所有修正记录"""
    path = _corrections_path(archive_dir)
    if not path.exists():
        return []
    records = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                logger.warning("跳过无效修正记录行: %s", line[:80])
    return records


def load_few_shot_examples(
    archive_dir: Path,
    current_text: str,
    max_examples: int = 3,
    max_tokens: int = 1000,
) -> str:
    """
    从修正记录中选取 few-shot 示例。

    优先选取与当前文书类型相似的修正记录。
    总 token 控制在 max_tokens 以内（按字符数粗略估算）。

    Returns:
        格式化的 few-shot 文本，若无记录则返回空字符串
    """
    records = load_corrections(archive_dir)
    if not records:
        return ""

    # 取最近 N 条（简单策略，后续可按 document_type 相似度排序）
    recent = records[-max_examples * 2 :]  # 多取一些备用
    examples_text = []
    total_chars = 0
    max_chars = max_tokens * APPROX_CHARS_PER_TOKEN

    for i, rec in enumerate(reversed(recent)):
        if len(examples_text) >= max_examples:
            break
        preview = rec.get("pdf_text_preview", "")
        correction = rec.get("user_correction", {})
        doc_type = correction.get("document_type", "未知")
        example = f'示例{i+1}:\n文书文本片段: "{preview[:200]}..."\n正确提取结果: {json.dumps(correction, ensure_ascii=False)}\n\n'
        if total_chars + len(example) > max_chars:
            break
        examples_text.append(example)
        total_chars += len(example)

    return "".join(reversed(examples_text)) if examples_text else ""
