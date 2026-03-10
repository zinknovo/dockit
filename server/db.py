"""PostgreSQL for users & usage (Supabase-compatible)"""

import os
from contextlib import contextmanager
from datetime import datetime, timezone

import psycopg2
from psycopg2.extras import RealDictCursor

DATABASE_URL = os.environ.get("DATABASE_URL", "")

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    tier VARCHAR(20) DEFAULT 'free',
    subscription_ends_at TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month VARCHAR(7) NOT NULL,
    count INTEGER DEFAULT 0,
    UNIQUE(user_id, month)
);

CREATE INDEX IF NOT EXISTS idx_usage_user_month ON usage(user_id, month);
"""

TIER_LIMITS = {"free": 50, "monthly": 999999, "annual": 999999}


def _get_conn():
    if not DATABASE_URL:
        raise RuntimeError("DATABASE_URL 未设置，请配置 Supabase 连接串")
    return psycopg2.connect(DATABASE_URL)


@contextmanager
def get_db():
    conn = _get_conn()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(SCHEMA)
        conn.commit()
        yield conn
    finally:
        conn.close()


def create_user(email: str, password_hash: str, tier: str = "free") -> int:
    with get_db() as c:
        with c.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                "INSERT INTO users (email, password_hash, tier) VALUES (%s, %s, %s) RETURNING id",
                (email, password_hash, tier),
            )
            uid = cur.fetchone()["id"]
        c.commit()
        return uid


def get_user_by_email(email: str) -> dict | None:
    with get_db() as c:
        with c.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("SELECT * FROM users WHERE email = %s", (email,))
            r = cur.fetchone()
            return dict(r) if r else None


def get_user_by_id(uid: int) -> dict | None:
    with get_db() as c:
        with c.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("SELECT * FROM users WHERE id = %s", (uid,))
            r = cur.fetchone()
            return dict(r) if r else None


def get_monthly_usage(uid: int) -> int:
    month = datetime.utcnow().strftime("%Y-%m")
    with get_db() as c:
        with c.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                "SELECT count FROM usage WHERE user_id = %s AND month = %s",
                (uid, month),
            )
            r = cur.fetchone()
            return int(r["count"]) if r else 0


def is_subscription_active(u: dict) -> bool:
    """付费用户需在有效期内"""
    tier = u.get("tier", "free")
    if tier == "free":
        return True
    ends = u.get("subscription_ends_at")
    if not ends:
        return False
    try:
        return datetime.fromisoformat(ends.replace("Z", "+00:00")) > datetime.now(
            timezone.utc
        )
    except (ValueError, TypeError):
        return False


def update_subscription(uid: int, tier: str, ends_at: str | None) -> None:
    """更新用户订阅（tier + 到期时间）"""
    with get_db() as c:
        with c.cursor() as cur:
            cur.execute(
                "UPDATE users SET tier = %s, subscription_ends_at = %s WHERE id = %s",
                (tier, ends_at, uid),
            )
        c.commit()


def increment_usage(uid: int) -> bool:
    """+1 usage, returns True if within limit"""
    month = datetime.utcnow().strftime("%Y-%m")
    with get_db() as c:
        with c.cursor() as cur:
            cur.execute(
                """
                INSERT INTO usage (user_id, month, count) VALUES (%s, %s, 1)
                ON CONFLICT (user_id, month) DO UPDATE SET count = usage.count + 1
                """,
                (uid, month),
            )
        c.commit()
    u = get_user_by_id(uid)
    limit = TIER_LIMITS.get(u["tier"], 50)
    usage = get_monthly_usage(uid)
    return usage <= limit
