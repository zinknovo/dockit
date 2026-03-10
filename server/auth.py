"""JWT auth"""

import os
from datetime import datetime, timedelta

import bcrypt
from jose import JWTError, jwt

SECRET = os.environ.get("DOCKIT_SECRET", "change-me-in-production")
ALGORITHM = "HS256"
EXPIRE_HOURS = 24 * 7
EXPIRE_HOURS_REMEMBER = 24 * 30  # 30 天


def hash_password(pw: str) -> str:
    return bcrypt.hashpw(pw.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(pw: str, h: str) -> bool:
    return bcrypt.checkpw(pw.encode("utf-8"), h.encode("utf-8"))


def create_token(uid: int, remember_me: bool = False) -> str:
    hours = EXPIRE_HOURS_REMEMBER if remember_me else EXPIRE_HOURS
    expire = datetime.utcnow() + timedelta(hours=hours)
    return jwt.encode({"sub": str(uid), "exp": expire}, SECRET, algorithm=ALGORITHM)


def decode_token(token: str) -> int | None:
    try:
        p = jwt.decode(token, SECRET, algorithms=[ALGORITHM])
        return int(p["sub"])
    except (JWTError, KeyError, ValueError):
        return None
