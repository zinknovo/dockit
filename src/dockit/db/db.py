# db.py - SQLite 数据库操作
"""案件、文书、开庭、期限的存储与查询"""

import json
import logging
from pathlib import Path

import sqlite3

from .models import DocumentInfo

logger = logging.getLogger(__name__)

SCHEMA = """
CREATE TABLE IF NOT EXISTS cases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_number VARCHAR(100) UNIQUE NOT NULL,
    cause_of_action VARCHAR(200),
    court_name VARCHAR(200),
    plaintiff TEXT,
    defendant TEXT,
    folder_name VARCHAR(300),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id INTEGER REFERENCES cases(id),
    document_type VARCHAR(50) NOT NULL,
    original_filename VARCHAR(500),
    archived_filename VARCHAR(500),
    archived_path VARCHAR(500),
    file_hash VARCHAR(64),
    extracted_data TEXT,
    source VARCHAR(50) DEFAULT 'auto_watch',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS court_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id INTEGER REFERENCES cases(id),
    session_time VARCHAR(50) NOT NULL,
    location VARCHAR(300),
    judge VARCHAR(100),
    panel_members TEXT,
    session_result VARCHAR(50) DEFAULT '待开庭',
    source_document_id INTEGER REFERENCES documents(id)
);

CREATE TABLE IF NOT EXISTS deadlines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id INTEGER REFERENCES cases(id),
    deadline_type VARCHAR(50) NOT NULL,
    due_date DATE NOT NULL,
    source_document_id INTEGER REFERENCES documents(id),
    is_completed INTEGER DEFAULT 0,
    reminder_days INTEGER DEFAULT 3,
    reminder_sent INTEGER DEFAULT 0,
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_documents_case_id ON documents(case_id);
CREATE INDEX IF NOT EXISTS idx_court_sessions_case_id ON court_sessions(case_id);
CREATE INDEX IF NOT EXISTS idx_deadlines_due ON deadlines(due_date);
"""


def _db_path(archive_dir: str | Path) -> Path:
    return Path(archive_dir).expanduser() / "dockit.db"


def get_connection(archive_dir: str | Path):
    path = _db_path(archive_dir)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path))
    conn.row_factory = sqlite3.Row
    return conn


def init_db(archive_dir: str | Path) -> None:
    """初始化数据库表"""
    conn = get_connection(archive_dir)
    try:
        conn.executescript(SCHEMA)
        try:
            conn.execute("ALTER TABLE documents ADD COLUMN file_hash VARCHAR(64)")
            conn.commit()
        except sqlite3.OperationalError:
            pass
        conn.commit()
        logger.info("数据库已初始化: %s", _db_path(archive_dir))
    finally:
        conn.close()


def list_deadlines(archive_dir: str | Path) -> list[dict]:
    """查询所有期限，用于 UI 展示。返回 case_number, cause_of_action, deadline_type, due_date, is_completed"""
    archive_dir = Path(archive_dir).expanduser()
    db_path = archive_dir / "dockit.db"
    if not db_path.exists():
        return []
    init_db(archive_dir)
    conn = get_connection(archive_dir)
    try:
        rows = conn.execute("""
            SELECT c.case_number, c.cause_of_action, d.deadline_type, d.due_date, d.is_completed
            FROM deadlines d
            JOIN cases c ON d.case_id = c.id
            ORDER BY d.due_date ASC
        """).fetchall()
        return [
            {
                "case_number": r[0],
                "cause_of_action": r[1],
                "deadline_type": r[2],
                "due_date": r[3],
                "is_completed": bool(r[4]),
            }
            for r in rows
        ]
    finally:
        conn.close()


def document_exists_by_hash(archive_dir: str | Path, file_hash: str) -> bool:
    """检查是否已有相同 hash 的文书（去重）"""
    conn = get_connection(archive_dir)
    try:
        init_db(archive_dir)
        r = conn.execute("SELECT 1 FROM documents WHERE file_hash = ?", (file_hash,)).fetchone()
        return r is not None
    finally:
        conn.close()


def upsert_case(
    conn: sqlite3.Connection,
    case_number: str,
    cause_of_action: str | None,
    court_name: str | None,
    plaintiff: str | None,
    defendant: str | None,
    folder_name: str | None,
) -> int:
    """插入或更新案件，返回 case_id"""
    conn.execute(
        """
        INSERT INTO cases (case_number, cause_of_action, court_name, plaintiff, defendant, folder_name, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(case_number) DO UPDATE SET
            cause_of_action=excluded.cause_of_action,
            court_name=excluded.court_name,
            plaintiff=excluded.plaintiff,
            defendant=excluded.defendant,
            folder_name=excluded.folder_name,
            updated_at=CURRENT_TIMESTAMP
        """,
        (case_number, cause_of_action or "", court_name or "", plaintiff or "", defendant or "", folder_name or ""),
    )
    row = conn.execute("SELECT id FROM cases WHERE case_number = ?", (case_number,)).fetchone()
    return row[0]


def insert_document(
    conn: sqlite3.Connection,
    case_id: int,
    document_type: str,
    original_filename: str,
    archived_filename: str,
    archived_path: str,
    extracted_data: dict,
    source: str = "auto_watch",
    file_hash: str | None = None,
) -> int:
    """插入文书记录，返回 document_id"""
    conn.execute(
        """
        INSERT INTO documents (case_id, document_type, original_filename, archived_filename, archived_path, file_hash, extracted_data, source)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (case_id, document_type, original_filename, archived_filename, archived_path, file_hash or "", json.dumps(extracted_data, ensure_ascii=False), source),
    )
    row = conn.execute("SELECT last_insert_rowid()").fetchone()
    return row[0]


def insert_court_session(
    conn: sqlite3.Connection,
    case_id: int,
    session_time: str,
    location: str | None,
    judge: str | None,
    panel_members: str | None,
    source_document_id: int | None,
) -> None:
    """插入开庭记录（仅当 hearing_time 存在时）"""
    if not session_time or not session_time.strip():
        return
    conn.execute(
        """
        INSERT INTO court_sessions (case_id, session_time, location, judge, panel_members, source_document_id)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (case_id, session_time, location or "", judge or "", json.dumps(panel_members if isinstance(panel_members, list) else [], ensure_ascii=False) if panel_members else "[]", source_document_id),
    )


def insert_deadline(
    conn: sqlite3.Connection,
    case_id: int,
    deadline_type: str,
    due_date: str,
    source_document_id: int | None,
    notes: str | None = None,
) -> None:
    """插入期限记录"""
    if not due_date or not due_date.strip():
        return
    conn.execute(
        """
        INSERT INTO deadlines (case_id, deadline_type, due_date, source_document_id, notes)
        VALUES (?, ?, ?, ?, ?)
        """,
        (case_id, deadline_type, due_date[:10], source_document_id, notes or ""),
    )


def file_hash(path: Path) -> str:
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def sync_from_archive(
    archive_dir: str | Path,
    doc_info: DocumentInfo,
    original_filename: str,
    archived_path: Path,
) -> None:
    """
    归档成功后同步到数据库：案件、文书、开庭、期限。
    """
    archive_dir = Path(archive_dir).expanduser()
    init_db(archive_dir)

    folder_tpl = "{case_number}_{cause_of_action}"
    folder_name = folder_tpl.format(
        case_number=doc_info.case_number or "",
        cause_of_action=doc_info.cause_of_action or "未知案由",
    )

    conn = get_connection(archive_dir)
    try:
        case_id = upsert_case(
            conn,
            case_number=doc_info.case_number or "",
            cause_of_action=doc_info.cause_of_action,
            court_name=doc_info.court_name,
            plaintiff=doc_info.plaintiff,
            defendant=doc_info.defendant,
            folder_name=folder_name,
        )
        doc_id = insert_document(
            conn,
            case_id=case_id,
            document_type=doc_info.document_type,
            original_filename=original_filename,
            archived_filename=archived_path.name,
            archived_path=str(archived_path),
            extracted_data=doc_info.raw_json,
            file_hash=file_hash(archived_path) if Path(archived_path).exists() else None,
        )
        insert_court_session(
            conn,
            case_id=case_id,
            session_time=doc_info.hearing_time or "",
            location=doc_info.hearing_location,
            judge=doc_info.judge,
            panel_members=doc_info.panel_members,
            source_document_id=doc_id,
        )
        for dtype, ddate in [
            ("举证期限", doc_info.evidence_deadline),
            ("答辩期限", doc_info.defense_deadline),
            ("上诉期限", doc_info.appeal_deadline),
        ]:
            if ddate:
                insert_deadline(conn, case_id, dtype, ddate, doc_id)
        conn.commit()
        logger.info("已同步到数据库: case_id=%s, doc_id=%s", case_id, doc_id)
    except Exception as e:
        conn.rollback()
        logger.exception("数据库同步失败: %s", e)
    finally:
        conn.close()
