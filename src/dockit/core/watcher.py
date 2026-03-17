# watcher.py - 文件监听逻辑
"""使用 watchdog 监听文件夹，检测到新 PDF 后触发归档流程"""

import logging
import sys
import time
from pathlib import Path

from watchdog.events import FileSystemEvent, FileSystemEventHandler
from watchdog.observers import Observer
from watchdog.observers.polling import PollingObserver

from .classifier import classify
from .extractor import extract_text
from .organizer import compute_target_path, move_file, move_to_unidentified
from .prefilter import is_likely_legal, log_prefilter_decision
from ..db.models import DocumentInfo
from ..db.db import sync_from_archive, document_exists_by_hash, file_hash as compute_file_hash, init_db
from ..tools.feedback import save_correction

logger = logging.getLogger(__name__)

# 新文件稳定等待时间（秒，下载完成/写入稳定后再处理）
SETTLE_SECONDS = 3


def _run_pipeline(
    config: dict,
    file_path: Path,
    on_classified,
    on_confirm,
) -> None:
    """
    执行单文件归档流程：提取 → 分类 → （确认）→ 移动。

    on_classified: 回调 (doc_info, target_path) -> 用户是否确认（confirm 模式下）
    on_confirm: 回调，用于 confirm 模式下用户编辑后的二次确认
    """
    archive_dir = Path(config["archive_dir"]).expanduser()
    archive_dir.mkdir(parents=True, exist_ok=True)
    extensions = set(e.lower() for e in config.get("extensions", [".pdf"]))

    try:
        logger.info("检测到新文件: %s", file_path)
        # 0. 去重：相同 hash 已归档则跳过
        init_db(archive_dir)
        fh = compute_file_hash(file_path)
        if document_exists_by_hash(config["archive_dir"], fh):
            logger.info("跳过重复文件（hash 已存在）: %s", file_path.name)
            return
        # 1. 提取文本（PDF/Word/图片OCR）
        text, valid = extract_text(file_path)
        logger.info("文本提取完成 (%d字)", len(text))
        if not valid:
            move_to_unidentified(archive_dir, file_path, "文本过短或OCR失败")
            return

        # 1.5 预筛：若非法律文书则跳过 LLM，减少 API 消耗
        prefilter_cfg = config.get("prefilter") or {}
        if prefilter_cfg.get("enabled", True):
            ok, reason = is_likely_legal(text)
            log_prefilter_decision(
                config["archive_dir"], file_path,
                "passed" if ok else "rejected", reason, len(text),
            )
            if not ok:
                logger.info("预筛跳过（%s）: %s", reason, file_path.name)
                return

        # 2. LLM 分类
        doc_info = classify(config, text)
        logger.info(
            "LLM 识别结果:\n  文书类型: %s\n  案号: %s\n  案由: %s\n  原告: %s\n  被告: %s\n  开庭时间: %s\n  开庭地点: %s",
            doc_info.document_type,
            doc_info.case_number or "（未识别）",
            doc_info.cause_of_action or "（未识别）",
            doc_info.plaintiff or "（未识别）",
            doc_info.defendant or "（未识别）",
            doc_info.hearing_time or "（未识别）",
            doc_info.hearing_location or "（未识别）",
        )

        # 3. 计算目标路径
        target = compute_target_path(config, doc_info, archive_dir, file_path.suffix or ".pdf")
        if target is None:
            move_to_unidentified(archive_dir, file_path, "无法识别案号")
            return

        # 4. 确认（confirm 模式下）
        confirmed, correction = on_classified(doc_info, target, text)
        if not confirmed:
            return  # 用户跳过

        # 若有修正，保存并应用
        if correction:
            llm_output = doc_info.raw_json
            user_correction = {
                "document_type": correction.get("document_type", doc_info.document_type),
                "case_number": correction.get("case_number", doc_info.case_number),
                "court_name": correction.get("court_name", doc_info.court_name),
                "plaintiff": correction.get("plaintiff", doc_info.plaintiff),
                "defendant": correction.get("defendant", doc_info.defendant),
                "document_date": correction.get("document_date", doc_info.document_date),
                "cause_of_action": correction.get("cause_of_action", doc_info.cause_of_action),
                "hearing_time": correction.get("hearing_time", doc_info.hearing_time),
                "hearing_location": correction.get("hearing_location", doc_info.hearing_location),
            }
            correction_fields = list(correction.keys())
            save_correction(
                archive_dir,
                file_path.name,
                llm_output,
                user_correction,
                correction_fields,
                text[:500],
            )
            # 用修正后的数据重建 DocumentInfo 并重新计算目标路径
            doc_info = DocumentInfo(
                document_type=user_correction["document_type"],
                case_number=user_correction["case_number"],
                court_name=user_correction["court_name"],
                plaintiff=user_correction["plaintiff"],
                defendant=user_correction["defendant"],
                document_date=user_correction["document_date"],
                cause_of_action=user_correction["cause_of_action"],
                hearing_time=user_correction["hearing_time"],
                hearing_location=user_correction["hearing_location"],
                evidence_deadline=doc_info.evidence_deadline,
                defense_deadline=doc_info.defense_deadline,
                appeal_deadline=doc_info.appeal_deadline,
                judge=doc_info.judge,
                panel_members=doc_info.panel_members,
                judgment_result=doc_info.judgment_result,
                judgment_amount=doc_info.judgment_amount,
                raw_json=doc_info.raw_json,
            )
            target = compute_target_path(config, doc_info, archive_dir, file_path.suffix or ".pdf")
            if target is None:
                move_to_unidentified(archive_dir, file_path, "修正后仍无法识别案号")
                return
            confirmed = on_confirm(doc_info, target)
            if not confirmed:
                return

        # 5. 移动文件
        move_file(file_path, target, copy_then_delete=True)
        # 6. 同步到数据库
        sync_from_archive(config["archive_dir"], doc_info, file_path.name, target)
        logger.info("✓ 归档完成")

    except Exception as e:
        logger.exception("归档流程失败: %s", e)
        try:
            move_to_unidentified(archive_dir, file_path, str(e))
        except Exception as e2:
            logger.exception("移入 _未识别 失败: %s", e2)


class DockitHandler(FileSystemEventHandler):
    """处理新文件的 watchdog 事件"""

    def __init__(self, config: dict, on_classified, on_confirm):
        self.config = config
        self.on_classified = on_classified
        self.on_confirm = on_confirm
        self.extensions = set(e.lower() for e in config.get("extensions", [".pdf"]))
        self._pending: dict[Path, float] = {}  # path -> mtime

    def _enqueue(self, path: Path, event_type: str = "") -> None:
        ext = path.suffix.lower() if path.suffix else ""
        if ext not in self.extensions:
            logger.info("忽略: %s (后缀=%s，需 .pdf/.docx/.jpg/.png)", path.name, path.suffix or "无")
            return
        self._pending[path] = time.time()
        logger.info("发现新文件 [%s]: %s", event_type, path.name)

    def on_created(self, event: FileSystemEvent) -> None:
        if event.is_directory:
            return
        self._enqueue(Path(event.src_path), "created")

    def on_moved(self, event: FileSystemEvent) -> None:
        """浏览器下载完成时多为重命名 .crdownload -> .pdf，触发 on_moved"""
        if event.is_directory:
            return
        self._enqueue(Path(event.dest_path), "moved")

    def _process_pending(self) -> None:
        now = time.time()
        to_process = []
        remaining = {}
        for p, t in self._pending.items():
            if now - t >= SETTLE_SECONDS:
                if p.exists():
                    to_process.append(p)
            else:
                remaining[p] = t
        self._pending = remaining
        for p in to_process:
            logger.info("开始处理: %s", p.name)
            _run_pipeline(self.config, p,
                self.on_classified,
                self.on_confirm,
            )


def _ensure_watch_dir_permission(config: dict, config_path: Path | None) -> None:
    """
    若监听目录未通过系统选择器取得权限（macOS .app 沙盒要求），则弹窗让用户选择。
    用户选中后更新 config 并持久化；取消则抛出 RuntimeError。
    """
    if sys.platform != "darwin" or not getattr(sys, "frozen", False):
        return
    if config.get("watch_dir_via_picker"):
        return
    from tkinter import Tk, filedialog
    root = Tk()
    root.withdraw()
    root.attributes("-topmost", True)
    initial = str(Path(config.get("watch_dir", "~/Downloads")).expanduser())
    folder = filedialog.askdirectory(
        title="请选择要监听的下载文件夹",
        initialdir=initial,
    )
    root.destroy()
    if not folder:
        raise RuntimeError("未选择监听目录，无法启动。请在弹窗中选择下载文件夹以获取访问权限。")
    config["watch_dir"] = folder
    config["watch_dir_via_picker"] = True
    if config_path:
        from ..config_path import update_config
        update_config(config_path, {"watch_dir": folder, "watch_dir_via_picker": True})


def start_watching(
    config: dict,
    on_classified,
    on_confirm,
    config_path: Path | None = None,
) -> Observer:
    """
    启动文件夹监听。macOS .app 下若监听目录未通过选择器取得权限，会先弹窗让用户选择。

    Returns:
        Observer 实例，调用 observer.join() 保持运行
    """
    import os
    _ensure_watch_dir_permission(config, config_path)
    watch_dir = Path(config["watch_dir"]).expanduser()
    watch_dir.mkdir(parents=True, exist_ok=True)

    handler = DockitHandler(config, on_classified, on_confirm)
    # macOS 上 FSEvents 在沙盒/受限环境会崩溃，默认用 PollingObserver；设 DOCKIT_USE_POLLING=0 可尝试 FSEvents
    _default_poll = sys.platform == "darwin" or os.environ.get("DOCKIT_USE_POLLING") == "1"
    use_polling = config.get("use_polling_observer", _default_poll)
    if use_polling:
        observer = PollingObserver()
    else:
        observer = Observer()
    observer.schedule(handler, str(watch_dir), recursive=True)
    observer.start()

    def poll_pending():
        while observer.is_alive():
            handler._process_pending()
            time.sleep(1)

    import threading
    t = threading.Thread(target=poll_pending, daemon=True)
    t.start()
    logger.info("监听目录: %s", watch_dir)
    return observer
