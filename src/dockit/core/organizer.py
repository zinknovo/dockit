# organizer.py - 文件重命名与移动逻辑
"""根据 DocumentInfo 生成目标路径并执行移动"""

import logging
import re
import shutil
from pathlib import Path

from ..db.models import DocumentInfo

logger = logging.getLogger(__name__)

# 文件名非法字符
INVALID_FILENAME_CHARS = re.compile(r'[/\\:*?"<>|]')


def _sanitize(s: str, max_len: int | None = None) -> str:
    """替换非法字符，可选截断"""
    s = INVALID_FILENAME_CHARS.sub("_", s)
    if max_len and len(s) > max_len:
        s = s[:max_len]
    return s


def _ensure_unique_path(dest: Path) -> Path:
    """若目标已存在，在文件名后加序号 (2)、(3)..."""
    if not dest.exists():
        return dest
    stem = dest.stem
    suffix = dest.suffix
    parent = dest.parent
    n = 2
    while True:
        candidate = parent / f"{stem}({n}){suffix}"
        if not candidate.exists():
            return candidate
        n += 1


def compute_target_path(
    config: dict,
    doc_info: DocumentInfo,
    archive_dir: Path,
    file_suffix: str = ".pdf",
) -> Path | None:
    """
    计算目标文件路径。

    Args:
        file_suffix: 输出文件扩展名，如 .pdf、.jpg
    Returns:
        目标 Path，若案号无法识别则返回 None
    """
    case_number = doc_info.case_number or ""
    cause = doc_info.cause_of_action or "未知案由"
    if not case_number.strip():
        return None

    max_len = config.get("max_party_name_length", 10)
    plaintiff = _sanitize(doc_info.plaintiff or "未知", max_len)
    defendant = _sanitize(doc_info.defendant or "未知", max_len)
    doc_type = _sanitize(doc_info.document_type)
    date = doc_info.document_date or "未知日期"

    folder_tpl = config.get("folder_template", "{case_number}_{cause_of_action}")
    filename_tpl = config.get("filename_template", "{document_type}_{plaintiff}v{defendant}_{date}")

    folder_name = folder_tpl.format(
        case_number=case_number,
        cause_of_action=cause,
    )
    folder_name = _sanitize(folder_name)

    filename = filename_tpl.format(
        document_type=doc_type,
        plaintiff=plaintiff,
        defendant=defendant,
        date=date,
    )
    filename = _sanitize(filename)

    case_dir = archive_dir / folder_name
    dest = case_dir / f"{filename}{file_suffix}"
    return _ensure_unique_path(dest)


def move_file(
    src_path: Path,
    dest_path: Path,
    copy_then_delete: bool = True,
) -> None:
    """
    将文件从 src 移动到 dest。
    使用复制再删除，避免跨磁盘移动失败导致文件丢失。
    """
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    if copy_then_delete:
        shutil.copy2(src_path, dest_path)
        src_path.unlink()
    else:
        shutil.move(src_path, dest_path)
    logger.info("归档完成: %s -> %s", src_path, dest_path)


def move_to_unidentified(archive_dir: Path, src_path: Path, reason: str = "") -> Path:
    """
    将文件移入 _未识别 文件夹。

    Returns:
        目标路径
    """
    unidentified_dir = archive_dir / "_未识别"
    unidentified_dir.mkdir(parents=True, exist_ok=True)
    dest = unidentified_dir / src_path.name
    dest = _ensure_unique_path(dest)
    shutil.copy2(src_path, dest)
    src_path.unlink()
    logger.warning("已移入 _未识别: %s%s", src_path, f" ({reason})" if reason else "")
    return dest
