"""Dockit API Server - 集成 LLM，用户认证，订阅计费"""

import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, EmailStr

from auth import create_token, decode_token, hash_password, verify_password
from db import (
    TIER_LIMITS,
    create_user,
    get_monthly_usage,
    get_user_by_email,
    get_user_by_id,
    increment_usage,
    is_subscription_active,
    update_subscription,
)
from billing import can_use_classify, get_user_limit
from llm import classify as llm_classify
from metrics import AUTH_TOTAL, CLASSIFY_ERRORS, CLASSIFY_TOTAL, metrics_content

app = FastAPI(title="Dockit API", version="0.1.0")
security = HTTPBearer(auto_error=False)


@app.get("/health")
def health():
    """Health check for Render / load balancers"""
    return {"status": "ok"}


class RegisterIn(BaseModel):
    email: EmailStr
    password: str
    remember_me: bool = False


class LoginIn(BaseModel):
    email: EmailStr
    password: str
    remember_me: bool = False


class ClassifyIn(BaseModel):
    text: str


def get_current_uid(
    cred: Optional[HTTPAuthorizationCredentials] = Depends(security),
) -> int:
    if not cred:
        raise HTTPException(401, "需要 Authorization: Bearer <token>")
    uid = decode_token(cred.credentials)
    if uid is None:
        raise HTTPException(401, "token 无效")
    u = get_user_by_id(uid)
    if not u:
        raise HTTPException(401, "用户不存在")
    return uid


@app.post("/api/auth/register")
def register(body: RegisterIn):
    if get_user_by_email(body.email):
        raise HTTPException(400, "邮箱已注册")
    uid = create_user(body.email, hash_password(body.password))
    token = create_token(uid, remember_me=body.remember_me)
    AUTH_TOTAL.labels(action="register").inc()
    return {"token": token, "tier": "free", "limit": get_user_limit({"tier": "free"})}


@app.post("/api/auth/login")
def login(body: LoginIn):
    u = get_user_by_email(body.email)
    if not u or not verify_password(body.password, u["password_hash"]):
        raise HTTPException(401, "邮箱或密码错误")
    AUTH_TOTAL.labels(action="login").inc()
    token = create_token(u["id"], remember_me=body.remember_me)
    return {
        "token": token,
        "tier": u["tier"],
        "limit": get_user_limit(u),
        "subscription_ends_at": u.get("subscription_ends_at"),
        "subscription_active": is_subscription_active(u),
    }


@app.get("/api/me")
def me(uid: int = Depends(get_current_uid)):
    """当前登录用户信息"""
    u = get_user_by_id(uid)
    if not u:
        raise HTTPException(401, "用户不存在")
    return {"email": u.get("email", "")}


@app.get("/api/usage")
def usage(uid: int = Depends(get_current_uid)):
    u = get_user_by_id(uid)
    limit = get_user_limit(u)
    count = get_monthly_usage(uid)
    sub_active = is_subscription_active(u)
    return {
        "tier": u["tier"],
        "used": count,
        "limit": limit,
        "subscription_ends_at": u.get("subscription_ends_at"),
        "subscription_active": sub_active,
    }


@app.post("/api/classify")
def classify(body: ClassifyIn, uid: int = Depends(get_current_uid)):
    u = get_user_by_id(uid)
    ok, err = can_use_classify(u)
    if not ok:
        raise HTTPException(402, err or "超出额度")

    try:
        result = llm_classify(body.text)
    except Exception as e:
        CLASSIFY_ERRORS.labels(reason="llm_error").inc()
        raise HTTPException(500, str(e))

    ok = increment_usage(uid)
    if not ok:
        CLASSIFY_ERRORS.labels(reason="quota_exceeded").inc()
        raise HTTPException(402, "超出额度")

    tier = u.get("tier", "free")
    CLASSIFY_TOTAL.labels(user_id=str(uid), tier=tier).inc()
    return result


class UpgradeIn(BaseModel):
    user_id: int
    tier: str  # monthly | annual
    ends_at: str  # ISO 格式，如 2025-04-01T00:00:00Z


def _require_admin(x_admin_secret: str | None = Header(None, alias="X-Admin-Secret")) -> None:
    secret = os.environ.get("DOCKIT_ADMIN_SECRET", "")
    if not secret or x_admin_secret != secret:
        raise HTTPException(403, "需要管理员密钥")


@app.post("/api/admin/upgrade-subscription")
def admin_upgrade(body: UpgradeIn, _: None = Depends(_require_admin)):
    """管理员升级用户订阅（需 X-Admin-Secret 头）"""
    u = get_user_by_id(body.user_id)
    if not u:
        raise HTTPException(404, "用户不存在")
    if body.tier not in ("monthly", "annual"):
        raise HTTPException(400, "tier 需为 monthly 或 annual")
    update_subscription(body.user_id, body.tier, body.ends_at)
    return {"ok": True, "user_id": body.user_id, "tier": body.tier, "ends_at": body.ends_at}


@app.get("/metrics")
def metrics():
    """Prometheus metrics endpoint"""
    body, content_type = metrics_content()
    return Response(content=body, media_type=content_type)
