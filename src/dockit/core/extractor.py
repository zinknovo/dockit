# extractor.py - 文本提取（PDF / Word / 图片 OCR）
"""PDF: pdfplumber | Word: python-docx | 图片: pytesseract OCR"""

import logging
from pathlib import Path

import pdfplumber

# 文本过短阈值（可能是扫描件）
MIN_TEXT_LENGTH = 50

# 支持提取的扩展名
SUPPORTED_EXTS = {".pdf", ".docx", ".jpg", ".jpeg", ".png"}

logger = logging.getLogger(__name__)


def _extract_from_pdf(path: Path) -> str:
    with pdfplumber.open(path) as pdf:
        parts = []
        for page in pdf.pages:
            t = page.extract_text()
            if t:
                parts.append(t)
        return "\n".join(parts)


def _extract_from_docx(path: Path) -> str:
    from docx import Document
    doc = Document(path)
    return "\n".join(p.text for p in doc.paragraphs if p.text)


def _extract_from_image(path: Path) -> str:
    try:
        import pytesseract
        from PIL import Image
        img = Image.open(path)
        # 中文 + 英文
        return pytesseract.image_to_string(img, lang="chi_sim+eng")
    except ImportError:
        logger.warning("OCR 需要: pip install pytesseract Pillow，且系统安装 tesseract (brew install tesseract tesseract-lang)")
        return ""
    except Exception as e:
        logger.exception("OCR 失败: %s", e)
        return ""


def extract_text(file_path: str | Path) -> tuple[str, bool]:
    """
    提取文件全文文本。支持 PDF、Word、图片。

    Returns:
        (提取的文本, 是否有效)
        若文本为空或过短（<50字），返回 (文本, False)
    """
    path = Path(file_path)
    if not path.exists():
        raise FileNotFoundError(f"文件不存在: {path}")
    ext = path.suffix.lower()
    if ext not in SUPPORTED_EXTS:
        raise ValueError(f"不支持的文件类型: {path}")

    try:
        if ext == ".pdf":
            full_text = _extract_from_pdf(path)
        elif ext == ".docx":
            full_text = _extract_from_docx(path)
        else:
            full_text = _extract_from_image(path)
    except Exception as e:
        logger.exception("提取失败: %s", e)
        raise

    text = full_text.strip()
    if len(text) < MIN_TEXT_LENGTH:
        logger.warning("文本过短 (%d字): %s", len(text), path)
        return text, False
    return text, True
