import hashlib
import hmac
import json
import os
import secrets
from datetime import datetime, timedelta
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import urlopen

from platform_storage import utc_now


WECHAT_CODE_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session"


class MiniappConfigurationError(RuntimeError):
    pass


class MiniappLoginError(RuntimeError):
    pass


def parse_utc(value):
    raw = str(value or "").strip()
    if not raw:
        return None
    try:
        return datetime.fromisoformat(raw.removesuffix("Z"))
    except ValueError:
        return None


def iso_after(seconds):
    return (datetime.utcnow() + timedelta(seconds=seconds)).replace(microsecond=0).isoformat() + "Z"


def epoch_millis(value):
    parsed = parse_utc(value)
    return int(parsed.timestamp() * 1000) if parsed is not None else 0


def exchange_wechat_code(code):
    app_id = os.environ.get("WJX_MINIAPP_APP_ID", "").strip()
    app_secret = os.environ.get("WJX_MINIAPP_APP_SECRET", "").strip()
    if not app_id or not app_secret:
        raise MiniappConfigurationError("微信小程序 AppID 或 AppSecret 尚未配置")
    login_code = str(code or "").strip()
    if not login_code or len(login_code) > 256:
        raise MiniappLoginError("微信登录凭证无效")
    query = urlencode(
        {
            "appid": app_id,
            "secret": app_secret,
            "js_code": login_code,
            "grant_type": "authorization_code",
        }
    )
    try:
        with urlopen(f"{WECHAT_CODE_SESSION_URL}?{query}", timeout=8) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (HTTPError, URLError, TimeoutError, ValueError) as error:
        raise MiniappLoginError("微信登录服务暂时不可用") from error
    if payload.get("errcode"):
        raise MiniappLoginError("微信登录凭证已失效，请重新进入小程序")
    openid = str(payload.get("openid") or "").strip()
    if not openid:
        raise MiniappLoginError("微信登录未返回用户标识")
    return {
        "openid": openid[:160],
        "unionid": str(payload.get("unionid") or "").strip()[:160],
    }


def opaque_token_hash(token):
    return hashlib.sha256(str(token or "").encode("utf-8")).hexdigest()


def binding_code_hash(code):
    secret = os.environ.get("WJX_PLATFORM_SECRET_KEY", "").encode("utf-8")
    normalized = normalize_binding_code(code).encode("ascii")
    return hmac.new(secret, normalized, hashlib.sha256).hexdigest()


def normalize_binding_code(value):
    return "".join(character for character in str(value or "").upper() if character.isalnum())[:8]


def session_ttl_seconds():
    raw = os.environ.get("WJX_MINIAPP_SESSION_DAYS", "30").strip()
    try:
        days = int(raw)
    except ValueError:
        days = 30
    return max(1, min(days, 90)) * 24 * 60 * 60


def binding_ttl_seconds():
    raw = os.environ.get("WJX_MINIAPP_BINDING_MINUTES", "10").strip()
    try:
        minutes = int(raw)
    except ValueError:
        minutes = 10
    return max(2, min(minutes, 30)) * 60


def upsert_wechat_user(conn, identity):
    now = utc_now()
    conn.execute(
        """
        INSERT INTO miniapp_users (openid, unionid, created_at, updated_at, last_login_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(openid) DO UPDATE SET
            unionid = COALESCE(NULLIF(excluded.unionid, ''), miniapp_users.unionid),
            updated_at = excluded.updated_at,
            last_login_at = excluded.last_login_at
        """,
        (identity["openid"], identity.get("unionid", ""), now, now, now),
    )
    return conn.execute(
        "SELECT * FROM miniapp_users WHERE openid = ?",
        (identity["openid"],),
    ).fetchone()


def issue_session(conn, user_id, remote_addr=""):
    token = secrets.token_urlsafe(32)
    now = utc_now()
    expires_at = iso_after(session_ttl_seconds())
    conn.execute("DELETE FROM miniapp_sessions WHERE expires_at <= ?", (now,))
    conn.execute(
        """
        INSERT INTO miniapp_sessions
            (user_id, token_hash, expires_at, created_at, last_seen_at, remote_addr)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (user_id, opaque_token_hash(token), expires_at, now, now, str(remote_addr or "")[:80]),
    )
    session_ids = conn.execute(
        "SELECT id FROM miniapp_sessions WHERE user_id = ? ORDER BY created_at DESC",
        (user_id,),
    ).fetchall()
    for stale in session_ids[5:]:
        conn.execute("DELETE FROM miniapp_sessions WHERE id = ?", (stale["id"],))
    return {"token": token, "expiresAt": epoch_millis(expires_at)}


def authenticated_user(conn, token, remote_addr=""):
    candidate = str(token or "").strip()
    if not candidate:
        return None
    now = utc_now()
    row = conn.execute(
        """
        SELECT u.*, s.id AS session_id, s.expires_at AS session_expires_at
        FROM miniapp_sessions s
        JOIN miniapp_users u ON u.id = s.user_id
        WHERE s.token_hash = ? AND s.expires_at > ?
        LIMIT 1
        """,
        (opaque_token_hash(candidate), now),
    ).fetchone()
    if row is None:
        return None
    conn.execute(
        "UPDATE miniapp_sessions SET last_seen_at = ?, remote_addr = ? WHERE id = ?",
        (now, str(remote_addr or "")[:80], row["session_id"]),
    )
    return row


def create_binding_code(conn, device_id, remote_addr=""):
    now = utc_now()
    expires_at = iso_after(binding_ttl_seconds())
    conn.execute(
        "DELETE FROM miniapp_binding_codes WHERE device_id = ? AND used_at IS NULL",
        (device_id,),
    )
    for _ in range(20):
        code = f"{secrets.randbelow(100_000_000):08d}"
        try:
            conn.execute(
                """
                INSERT INTO miniapp_binding_codes
                    (device_id, code_hash, expires_at, created_at, remote_addr)
                VALUES (?, ?, ?, ?, ?)
                """,
                (
                    device_id,
                    binding_code_hash(code),
                    expires_at,
                    now,
                    str(remote_addr or "")[:80],
                ),
            )
            return {"bindingCode": code, "expiresAt": epoch_millis(expires_at)}
        except Exception as error:
            if "UNIQUE constraint failed" not in str(error):
                raise
    raise RuntimeError("暂时无法生成绑定码，请稍后重试")


def consume_binding_code(conn, user_id, code):
    normalized = normalize_binding_code(code)
    if len(normalized) < 6:
        return None
    now = utc_now()
    row = conn.execute(
        """
        SELECT * FROM miniapp_binding_codes
        WHERE code_hash = ? AND used_at IS NULL AND expires_at > ?
        LIMIT 1
        """,
        (binding_code_hash(normalized), now),
    ).fetchone()
    if row is None:
        return None
    conn.execute("DELETE FROM miniapp_vehicle_bindings WHERE user_id = ?", (user_id,))
    conn.execute("DELETE FROM miniapp_vehicle_bindings WHERE device_id = ?", (row["device_id"],))
    conn.execute(
        """
        INSERT INTO miniapp_vehicle_bindings (user_id, device_id, bound_at, updated_at)
        VALUES (?, ?, ?, ?)
        """,
        (user_id, row["device_id"], now, now),
    )
    conn.execute(
        """
        UPDATE miniapp_binding_codes
        SET used_at = ?, used_by_user_id = ?
        WHERE id = ?
        """,
        (now, user_id, row["id"]),
    )
    return conn.execute("SELECT * FROM devices WHERE id = ?", (row["device_id"],)).fetchone()


def bound_device(conn, user_id):
    return conn.execute(
        """
        SELECT d.*
        FROM miniapp_vehicle_bindings b
        JOIN devices d ON d.id = b.device_id
        WHERE b.user_id = ?
        LIMIT 1
        """,
        (user_id,),
    ).fetchone()


def mask_vin(value):
    vin = str(value or "")
    if len(vin) < 8:
        return vin
    return f"{vin[:4]}****{vin[-4:]}"
