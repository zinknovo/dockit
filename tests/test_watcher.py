"""Tests for watcher - 文件监听与轮询逻辑"""

import time
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from dockit.core.watcher import DockitHandler, SETTLE_SECONDS, start_watching


class MockEvent:
    def __init__(self, src_path: str, dest_path: str = "", is_directory: bool = False):
        self.src_path = src_path
        self.dest_path = dest_path or src_path
        self.is_directory = is_directory


def test_enqueue_accepts_pdf(config, tmp_path):
    """PDF 文件应入队"""
    handler = DockitHandler(config, MagicMock(), MagicMock())
    p = tmp_path / "test.pdf"
    handler._enqueue(p, "created")
    assert len(handler._pending) == 1
    assert p in handler._pending


def test_enqueue_rejects_non_pdf(config, tmp_path):
    """非 PDF 文件应被忽略"""
    handler = DockitHandler(config, MagicMock(), MagicMock())
    handler._enqueue(tmp_path / "test.txt", "created")
    handler._enqueue(tmp_path / "foo", "created")
    assert len(handler._pending) == 0


def test_enqueue_accepts_uppercase_pdf(config, tmp_path):
    """大写 .PDF 也应入队"""
    handler = DockitHandler(config, MagicMock(), MagicMock())
    p = tmp_path / "test.PDF"
    handler._enqueue(p, "created")
    assert len(handler._pending) == 1


def test_on_created_adds_pdf(config, tmp_path):
    """on_created 对 PDF 调用 _enqueue"""
    handler = DockitHandler(config, MagicMock(), MagicMock())
    p = str(tmp_path / "x.pdf")
    handler.on_created(MockEvent(p))
    assert len(handler._pending) == 1


def test_on_created_ignores_directory(config, tmp_path):
    """on_created 忽略目录"""
    handler = DockitHandler(config, MagicMock(), MagicMock())
    handler.on_created(MockEvent(str(tmp_path), is_directory=True))
    assert len(handler._pending) == 0


def test_on_moved_adds_dest_pdf(config, tmp_path):
    """on_moved 对目标 PDF 入队（浏览器下载完成场景）"""
    handler = DockitHandler(config, MagicMock(), MagicMock())
    src = str(tmp_path / "foo.crdownload")
    dest = str(tmp_path / "foo.pdf")
    handler.on_moved(MockEvent(src, dest))
    assert Path(dest) in handler._pending


def test_process_pending_waits_settle_seconds(config, tmp_path):
    """未满 SETTLE_SECONDS 的项不处理"""
    handler = DockitHandler(config, MagicMock(), MagicMock())
    p = tmp_path / "notexist.pdf"
    handler._pending[p] = time.time()
    handler._process_pending()
    assert len(handler._pending) == 1


def test_process_pending_processes_after_settle(tmp_path, config):
    """满 SETTLE_SECONDS 且文件存在时应触发处理"""
    pdf = tmp_path / "doc.pdf"
    pdf.write_bytes(b"dummy")
    config["archive_dir"] = str(tmp_path / "archive")
    config["watch_dir"] = str(tmp_path)
    config["prefilter"] = {"enabled": False}

    pipeline_called = []
    def fake_on_classified(doc_info, target, text):
        pipeline_called.append(("classified", doc_info))
        return True, None
    def fake_on_confirm(doc_info, target):
        return True

    with patch("dockit.core.watcher.extract_text", return_value=("x" * 100, True)), \
         patch("dockit.core.watcher.classify") as mock_classify, \
         patch("dockit.core.watcher.compute_target_path") as mock_compute, \
         patch("dockit.core.watcher.move_file"), \
         patch("dockit.ui.tray.notify_main"):
        from dockit.db.models import DocumentInfo
        mock_classify.return_value = DocumentInfo(
            document_type="传票",
            case_number="（2024）京0105民初12345号",
            court_name="朝阳法院",
            plaintiff="张三",
            defendant="某某公司",
            document_date="2024-03-01",
            cause_of_action="合同纠纷",
            hearing_time=None,
            hearing_location=None,
            evidence_deadline=None,
            defense_deadline=None,
            appeal_deadline=None,
            judge=None,
            panel_members=None,
            judgment_result=None,
            judgment_amount=None,
            raw_json={},
        )
        mock_compute.return_value = tmp_path / "archive" / "out.pdf"

        handler = DockitHandler(config, fake_on_classified, fake_on_confirm)
        handler._pending[pdf] = time.time() - SETTLE_SECONDS - 1
        handler._process_pending()

    assert len(pipeline_called) == 1


def test_process_pending_prefilter_rejects(tmp_path, config):
    """预筛不通过时，不调用 classify，文件不归档"""
    pdf = tmp_path / "receipt.pdf"
    pdf.write_bytes(b"dummy")
    config["archive_dir"] = str(tmp_path / "archive")
    config["watch_dir"] = str(tmp_path)
    config["prefilter"] = {"enabled": True}

    classified_calls = []

    def fake_on_classified(doc_info, target, text):
        classified_calls.append(doc_info)
        return True, None

    with patch("dockit.core.watcher.extract_text", return_value=("购物小票 商品xxx 谢谢惠顾" * 10, True)), \
         patch("dockit.core.watcher.classify") as mock_classify, \
         patch("dockit.core.watcher.log_prefilter_decision"):
        handler = DockitHandler(config, fake_on_classified, MagicMock(return_value=True))
        handler._pending[pdf] = time.time() - SETTLE_SECONDS - 1
        handler._process_pending()

    assert len(classified_calls) == 0
    mock_classify.assert_not_called()


def test_process_pending_skips_duplicate_by_hash(tmp_path, config):
    """hash 已存在时跳过重复文件"""
    pdf = tmp_path / "dup.pdf"
    pdf.write_bytes(b"same content")
    config["archive_dir"] = str(tmp_path / "archive")
    config["watch_dir"] = str(tmp_path)
    config["prefilter"] = {"enabled": False}

    classified_calls = []

    def fake_on_classified(doc_info, target, text):
        classified_calls.append(doc_info)
        return True, None

    with patch("dockit.core.watcher.extract_text", return_value=("x" * 100, True)), \
         patch("dockit.core.watcher.document_exists_by_hash", return_value=True), \
         patch("dockit.core.watcher.classify"), \
         patch("dockit.core.watcher.compute_target_path"):
        handler = DockitHandler(config, fake_on_classified, MagicMock(return_value=True))
        handler._pending[pdf] = time.time() - SETTLE_SECONDS - 1
        handler._process_pending()

    assert len(classified_calls) == 0


def test_process_pending_invalid_text_moves_to_unidentified(tmp_path, config):
    """文本过短时移入未识别"""
    pdf = tmp_path / "short.pdf"
    pdf.write_bytes(b"%PDF-1.4 x")
    archive = tmp_path / "archive"
    config["archive_dir"] = str(archive)
    config["watch_dir"] = str(tmp_path)
    config["prefilter"] = {"enabled": False}

    with patch("dockit.core.watcher.extract_text", return_value=("hi", False)), \
         patch("dockit.core.watcher.document_exists_by_hash", return_value=False), \
         patch("dockit.core.watcher.move_to_unidentified") as mock_move:
        handler = DockitHandler(config, MagicMock(), MagicMock())
        handler._pending[pdf] = time.time() - SETTLE_SECONDS - 1
        handler._process_pending()

    mock_move.assert_called_once()


def test_process_pending_no_case_number_moves_to_unidentified(tmp_path, config):
    """无法识别案号时移入未识别"""
    pdf = tmp_path / "no_case.pdf"
    pdf.write_bytes(b"%PDF-1.4 x")
    config["archive_dir"] = str(tmp_path / "archive")
    config["watch_dir"] = str(tmp_path)
    config["prefilter"] = {"enabled": False}

    with patch("dockit.core.watcher.extract_text", return_value=("x" * 100, True)), \
         patch("dockit.core.watcher.document_exists_by_hash", return_value=False), \
         patch("dockit.core.watcher.classify") as mock_classify, \
         patch("dockit.core.watcher.compute_target_path", return_value=None), \
         patch("dockit.core.watcher.move_to_unidentified") as mock_move:
        from dockit.db.models import DocumentInfo
        mock_classify.return_value = DocumentInfo(
            document_type="传票",
            case_number=None,
            court_name="",
            plaintiff="",
            defendant="",
            document_date="",
            cause_of_action="",
            hearing_time=None,
            hearing_location=None,
            evidence_deadline=None,
            defense_deadline=None,
            appeal_deadline=None,
            judge=None,
            panel_members=None,
            judgment_result=None,
            judgment_amount=None,
            raw_json={},
        )
        handler = DockitHandler(config, MagicMock(), MagicMock())
        handler._pending[pdf] = time.time() - SETTLE_SECONDS - 1
        handler._process_pending()

    mock_move.assert_called_once()


def test_process_pending_user_skips_when_not_confirmed(tmp_path, config):
    """用户取消确认时不移动文件"""
    pdf = tmp_path / "skip.pdf"
    pdf.write_bytes(b"%PDF-1.4 x")
    config["archive_dir"] = str(tmp_path / "archive")
    config["watch_dir"] = str(tmp_path)
    config["prefilter"] = {"enabled": False}

    def fake_on_classified(doc_info, target, text):
        return False, None  # 用户取消

    with patch("dockit.core.watcher.extract_text", return_value=("x" * 100, True)), \
         patch("dockit.core.watcher.document_exists_by_hash", return_value=False), \
         patch("dockit.core.watcher.classify") as mock_classify, \
         patch("dockit.core.watcher.compute_target_path", return_value=tmp_path / "archive" / "out.pdf"), \
         patch("dockit.core.watcher.move_file") as mock_move:
        from dockit.db.models import DocumentInfo
        mock_classify.return_value = DocumentInfo(
            document_type="传票",
            case_number="（2024）京0105民初1号",
            court_name="",
            plaintiff="",
            defendant="",
            document_date="",
            cause_of_action="",
            hearing_time=None,
            hearing_location=None,
            evidence_deadline=None,
            defense_deadline=None,
            appeal_deadline=None,
            judge=None,
            panel_members=None,
            judgment_result=None,
            judgment_amount=None,
            raw_json={},
        )
        handler = DockitHandler(config, fake_on_classified, MagicMock())
        handler._pending[pdf] = time.time() - SETTLE_SECONDS - 1
        handler._process_pending()

    mock_move.assert_not_called()


@pytest.mark.slow
def test_start_watching_observer_alive_so_poll_runs(config, tmp_path):
    """CRITICAL: start_watching 返回时 observer 必须已 start，这样 poll 线程的 observer.is_alive() 为 True"""
    config["watch_dir"] = str(tmp_path)
    config["archive_dir"] = str(tmp_path / "archive")
    tmp_path.mkdir(exist_ok=True)

    obs = start_watching(config, MagicMock(return_value=(True, None)), MagicMock(return_value=True))
    assert obs.is_alive(), "observer 必须已启动，否则 poll 线程会立即退出"
    obs.stop()
    obs.join(timeout=2)
