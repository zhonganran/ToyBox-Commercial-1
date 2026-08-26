import os
import hashlib
import json
import secrets
import sqlite3
import time
from datetime import datetime, timedelta
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from html import escape
from pathlib import Path
from urllib.parse import urlsplit

from flask import (
    Flask,
    after_this_request,
    abort,
    flash,
    jsonify,
    redirect,
    request,
    send_file,
    send_from_directory,
    session,
    url_for,
)
from werkzeug.security import check_password_hash, generate_password_hash
from werkzeug.middleware.proxy_fix import ProxyFix
from werkzeug.utils import secure_filename
from platform_config import (
    ADMIN_TITLE,
    ALLOWED_IMAGE_EXTENSIONS,
    ALLOWED_LOG_EXTENSIONS,
    APK_DIR,
    DB_PATH,
    DEFAULT_CONFIG,
    DEFAULT_TRIAL_DAYS,
    LICENSE_LOCK_SECONDS,
    LICENSE_MAX_FAILURES,
    LOGIN_LOCK_SECONDS,
    LOGIN_MAX_FAILURES,
    LOG_DIR,
    LOG_UPLOAD_DEVICE_LIMIT,
    LOG_UPLOAD_IP_LIMIT,
    MAX_LOG_SIZE,
    MAX_SERVICE_QR_SIZE,
    MAX_UPLOAD_SIZE,
    PASSWORD_HASH_METHOD,
    PAYMENT_ACTIVATION_PRICE,
    PAYMENT_ORDER_TTL_SECONDS,
    PAYMENT_PID,
    PAYMENT_QUERY_INTERVAL_SECONDS,
    PAYMENT_SITE_NAME,
    PUBLIC_API_BASE_URL,
    PUBLIC_CDN_BASE_URL,
    SERVICE_QR_DIR,
    SERVICE_QR_TYPES,
    TRUSTED_PROXY_COUNT,
)
from platform_identity import extract_valid_vin, normalize_device_code
from platform_apk import ApkValidationError, inspect_apk
from platform_license import (
    add_days_from_iso,
    add_days_iso,
    build_offline_license,
    epoch_millis,
    format_license_code,
    generate_license_code,
    is_valid_license_code,
    license_row_activated_at,
    load_offline_license_private_key,
    mask_license_code,
    normalize_license_code,
    offline_license_private_key_status,
    parse_utc,
    sign_offline_license,
    verify_offline_license_proof,
)
from platform_payment import (
    PaymentConfigurationError,
    PaymentProviderError,
    create_order as create_provider_order,
    payment_ready,
    query_order as query_provider_order,
    verify_params as verify_payment_params,
)
from platform_storage import (
    configured_admin_credentials,
    create_database_backup,
    db,
    init_db,
    utc_datetime,
    utc_now,
)


def configured_secret_key():
    value = os.environ.get("WJX_PLATFORM_SECRET_KEY", "").strip()
    if not value:
        raise RuntimeError("WJX_PLATFORM_SECRET_KEY must be configured before platform startup")
    return value


app = Flask(__name__)
if TRUSTED_PROXY_COUNT > 0:
    app.wsgi_app = ProxyFix(app.wsgi_app, x_for=TRUSTED_PROXY_COUNT)
app.secret_key = configured_secret_key()
app.config["MAX_CONTENT_LENGTH"] = MAX_UPLOAD_SIZE
app.config["SESSION_COOKIE_HTTPONLY"] = True
app.config["SESSION_COOKIE_SAMESITE"] = "Lax"
app.config["SESSION_COOKIE_SECURE"] = os.environ.get("WJX_PLATFORM_COOKIE_SECURE", "1") != "0"
app.config["PERMANENT_SESSION_LIFETIME"] = timedelta(hours=12)

def config_get(conn, key, default=""):
    row = conn.execute("SELECT value FROM config WHERE key = ?", (key,)).fetchone()
    return row["value"] if row else default


def config_set(conn, key, value):
    conn.execute(
        "INSERT INTO config (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        (key, value),
    )


def h(value):
    return escape("" if value is None else str(value), quote=True)


def client_key():
    return request.remote_addr or "unknown"


def csrf_token():
    token = session.get("csrf_token")
    if not token:
        token = secrets.token_urlsafe(32)
        session["csrf_token"] = token
    return token


def csrf_input():
    return f'<input type="hidden" name="csrf_token" value="{h(csrf_token())}">'


def validate_csrf():
    expected = session.get("csrf_token", "")
    submitted = request.form.get("csrf_token", "")
    return bool(expected and submitted and secrets.compare_digest(expected, submitted))


def login_lock_remaining(conn, key):
    row = conn.execute("SELECT locked_until FROM login_attempts WHERE client_key = ?", (key,)).fetchone()
    if row is None:
        return 0
    return max(0, int(row["locked_until"]) - int(time.time()))


def record_login_success(conn, key):
    conn.execute("DELETE FROM login_attempts WHERE client_key = ?", (key,))


def record_login_failure(conn, key):
    now_epoch = int(time.time())
    row = conn.execute("SELECT fail_count, locked_until FROM login_attempts WHERE client_key = ?", (key,)).fetchone()
    if row and int(row["locked_until"]) > now_epoch:
        return int(row["locked_until"]) - now_epoch
    fail_count = (int(row["fail_count"]) if row else 0) + 1
    locked_until = now_epoch + LOGIN_LOCK_SECONDS if fail_count >= LOGIN_MAX_FAILURES else 0
    conn.execute(
        """
        INSERT INTO login_attempts (client_key, fail_count, locked_until, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(client_key) DO UPDATE SET
            fail_count = excluded.fail_count,
            locked_until = excluded.locked_until,
            updated_at = excluded.updated_at
        """,
        (key, fail_count, locked_until, utc_now()),
    )
    return max(0, locked_until - now_epoch)


def api_attempt_key(scope, device_code=""):
    normalized_device = normalize_device_code(device_code)[:64]
    return f"{scope}:{client_key()}:{normalized_device}"


def api_lock_remaining(conn, key):
    row = conn.execute("SELECT locked_until FROM api_attempts WHERE client_key = ?", (key,)).fetchone()
    if row is None:
        return 0
    return max(0, int(row["locked_until"]) - int(time.time()))


def record_api_success(conn, key):
    conn.execute("DELETE FROM api_attempts WHERE client_key = ?", (key,))


def record_api_failure(conn, key, max_failures=LICENSE_MAX_FAILURES, lock_seconds=LICENSE_LOCK_SECONDS):
    now_epoch = int(time.time())
    row = conn.execute("SELECT fail_count, locked_until FROM api_attempts WHERE client_key = ?", (key,)).fetchone()
    if row and int(row["locked_until"]) > now_epoch:
        return int(row["locked_until"]) - now_epoch
    fail_count = (int(row["fail_count"]) if row else 0) + 1
    locked_until = now_epoch + lock_seconds if fail_count >= max_failures else 0
    conn.execute(
        """
        INSERT INTO api_attempts (client_key, fail_count, locked_until, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(client_key) DO UPDATE SET
            fail_count = excluded.fail_count,
            locked_until = excluded.locked_until,
            updated_at = excluded.updated_at
        """,
        (key, fail_count, locked_until, utc_now()),
    )
    return max(0, locked_until - now_epoch)


def config_bool(conn, key, default=True):
    value = config_get(conn, key, "1" if default else "0").strip().lower()
    return value in {"1", "true", "yes", "on", "enabled"}


def config_int(conn, key, default):
    try:
        return int(config_get(conn, key, str(default)).strip())
    except ValueError:
        return default


def bounded_int(value, default, minimum, maximum):
    try:
        parsed = int(str(value).strip())
    except (TypeError, ValueError):
        return default
    return max(minimum, min(parsed, maximum))


def log_event(conn, kind, message, remote_addr=None):
    conn.execute(
        "INSERT INTO audit_logs (type, message, remote_addr, created_at) VALUES (?, ?, ?, ?)",
        (kind, message, remote_addr if remote_addr is not None else request.remote_addr or "", utc_now()),
    )


@app.before_request
def ensure_ready():
    init_db()
    if (
        request.path == "/api/v1/logs/upload"
        and request.content_length
        and request.content_length > MAX_LOG_SIZE + 256 * 1024
    ):
        return jsonify({
            "success": False,
            "message": f"日志不能超过 {MAX_LOG_SIZE // 1024 // 1024} MB",
        }), 413
    if request.method in {"POST", "PUT", "PATCH", "DELETE"} and not request.path.startswith("/api/"):
        if not validate_csrf():
            abort(400, "CSRF token invalid")


def current_admin():
    return session.get("admin")


def require_login():
    if not current_admin():
        return redirect(url_for("login", next=request.full_path))
    return None


def safe_next_target(value):
    target = (value or "").strip()
    if not target or not target.startswith("/") or target.startswith("//") or "\\" in target:
        return None
    parsed = urlsplit(target)
    if parsed.scheme or parsed.netloc:
        return None
    return target


def public_url(path):
    if path.startswith("/cdn/"):
        return f"{PUBLIC_CDN_BASE_URL}{path}"
    return f"{PUBLIC_API_BASE_URL}{path}"


def allowed_image_file(filename):
    suffix = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    return suffix in ALLOWED_IMAGE_EXTENSIONS


def allowed_log_file(filename):
    suffix = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    return suffix in ALLOWED_LOG_EXTENSIONS


def save_limited_log(upload, target, max_size):
    total = 0
    try:
        with target.open("xb") as output:
            while True:
                chunk = upload.stream.read(64 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > max_size:
                    raise ValueError(f"日志不能超过 {max_size // 1024 // 1024} MB")
                output.write(chunk)
        if total <= 0:
            raise ValueError("日志文件为空")
        target.chmod(0o640)
        return total
    except Exception:
        target.unlink(missing_ok=True)
        raise


def collect_device_codes(payload):
    # Device identity is singular. Never trust client-supplied alias collections:
    # an attacker could otherwise append its own VIN to an activated VIN and have
    # the server sign the whole group as one entitlement.
    for key in ("deviceCode", "rawDeviceCode"):
        code = extract_valid_vin(payload.get(key))
        if code:
            return [code]
    return []


DEVICE_CREDENTIAL_VERSION = 1


def requested_device_credential(payload):
    return bounded_int(
        payload.get("credentialVersion"),
        default=0,
        minimum=0,
        maximum=DEVICE_CREDENTIAL_VERSION,
    ) >= DEVICE_CREDENTIAL_VERSION


def request_device_token():
    authorization = request.headers.get("Authorization", "").strip()
    scheme, separator, token = authorization.partition(" ")
    if not separator or scheme.lower() != "bearer":
        return ""
    return token.strip()[:256]


def device_token_hash(token):
    return hashlib.sha256(str(token or "").encode("utf-8")).hexdigest()


def device_token_matches(row, token):
    stored = str(row["api_token_hash"] or "") if row is not None else ""
    candidate = device_token_hash(token) if token else ""
    return bool(stored and candidate and secrets.compare_digest(stored, candidate))


def issue_device_token(conn, device_codes):
    codes = list(dict.fromkeys(code for code in device_codes if code))
    if not codes:
        return ""
    token = secrets.token_urlsafe(32)
    conn.execute(
        f"UPDATE devices SET api_token_hash = ?, api_token_issued_at = ? "
        f"WHERE device_code IN ({sql_placeholders(codes)})",
        (device_token_hash(token), utc_now(), *codes),
    )
    return token


def resolve_device_token(
    conn,
    device_codes,
    presented_token,
    allow_reissue=False,
    rotate=False,
):
    codes = list(dict.fromkeys(code for code in device_codes if code))
    if not codes:
        return ""
    rows = conn.execute(
        f"SELECT * FROM devices WHERE device_code IN ({sql_placeholders(codes)})",
        codes,
    ).fetchall()
    if rotate:
        return issue_device_token(conn, codes)
    if presented_token and any(device_token_matches(row, presented_token) for row in rows):
        conn.execute(
            f"UPDATE devices SET api_token_hash = ?, "
            f"api_token_issued_at = COALESCE(api_token_issued_at, ?) "
            f"WHERE device_code IN ({sql_placeholders(codes)})",
            (device_token_hash(presented_token), utc_now(), *codes),
        )
        return presented_token
    if allow_reissue:
        return issue_device_token(conn, codes)
    return ""


def sql_placeholders(values):
    return ",".join("?" for _ in values)


def latest_release():
    with db() as conn:
        return conn.execute(
            """
            SELECT * FROM releases
            WHERE enabled = 1
            ORDER BY version_code DESC, id DESC
            LIMIT 1
            """
        ).fetchone()


def service_qr_to_api(row):
    image_url = ""
    if row["filename"]:
        image_url = public_url(f"/cdn/service-qrcodes/{row['key']}?v={int(epoch_millis(row['updated_at']) or time.time() * 1000)}")
    return {
        "key": row["key"],
        "title": row["title"],
        "subtitle": row["subtitle"],
        "description": row["description"],
        "enabled": bool(row["enabled"]),
        "imageUrl": image_url,
        "expiresAt": row["expires_at"],
        "updatedAt": row["updated_at"],
    }


def format_size(value):
    size = int(value or 0)
    if size >= 1024 * 1024:
        return f"{size / 1024 / 1024:.1f} MB"
    if size >= 1024:
        return f"{size / 1024:.1f} KB"
    return f"{size} B"


def is_recent_online(value, minutes=15):
    parsed = parse_utc(value)
    if parsed is None:
        return False
    return parsed >= utc_datetime() - timedelta(minutes=minutes)


def device_authorization(conn, device):
    license_row = conn.execute(
        """
        SELECT * FROM license_codes
        WHERE enabled = 1 AND device_code = ?
        ORDER BY activated_at DESC, id DESC LIMIT 1
        """,
        (device["device_code"],),
    ).fetchone()
    trial_end = parse_utc(device["trial_ends_at"])
    trial_active = bool(trial_end and trial_end > utc_datetime())
    return {
        # The enabled license row is authoritative. The devices.activated column is only a cache
        # refreshed by client traffic and must not keep an offline vehicle activated after an
        # administrator disables or deletes its license.
        "activated": license_row is not None,
        "trial_active": trial_active,
        "license": license_row,
    }


def refresh_cached_device_activation(conn, device_code):
    code = extract_valid_vin(device_code)
    if not code:
        return
    active = conn.execute(
        "SELECT 1 FROM license_codes WHERE enabled = 1 AND device_code = ? LIMIT 1",
        (code,),
    ).fetchone() is not None
    conn.execute(
        "UPDATE devices SET activated = ? WHERE device_code = ?",
        (1 if active else 0, code),
    )


def authorization_category(authorization):
    if authorization["activated"]:
        return "activated", "已激活", "green"
    if authorization["trial_active"]:
        return "trial", "试用", "orange"
    return "expired", "已到期", "gray"


def status_badge(text, tone="neutral"):
    return f'<span class="badge {h(tone)}">{h(text)}</span>'


def page(title, active, body):
    nav = [
        ("dashboard", "总览", url_for("index"), "D"),
        ("updates", "更新管理", url_for("updates"), "U"),
        ("licenses", "激活授权", url_for("licenses"), "L"),
        ("payments", "支付订单", url_for("payment_orders"), "P"),
        ("service", "服务授权", url_for("service_qrcodes"), "Q"),
        ("vehicles", "车辆在线", url_for("vehicles"), "V"),
        ("diagnostic_logs", "诊断日志", url_for("diagnostic_logs"), "G"),
        ("settings", "系统设置", url_for("settings"), "C"),
    ]
    nav_html = "".join(
        f'<a class="nav-item {"active" if key == active else ""}" href="{h(href)}"><span>{h(icon)}</span><b>{h(label)}</b></a>'
        for key, label, href, icon in nav
    )
    logout = ""
    if current_admin():
        logout = f'<form method="post" action="/logout">{csrf_input()}<button class="ghost full" type="submit">退出登录</button></form>'
    messages = ""
    flashed = session.pop("_flashes", []) if "_flashes" in session else []
    if flashed:
        messages = '<div class="flash-stack">' + "".join(
            f'<div class="flash {h(category)}">{h(message)}</div>' for category, message in flashed
        ) + "</div>"
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{h(title)} - {h(ADMIN_TITLE)}</title>
  <link rel="stylesheet" href="{h(url_for('static', filename='platform.css'))}">
</head>
<body>
  <aside class="sidebar">
    <div class="brand"><span class="logo-mark"></span><div><strong>超级玩具箱</strong><small>统一商业平台</small></div></div>
    <nav>{nav_html}</nav>
    <div class="sidebar-foot">
      <small>管理员</small>
      <strong>{h(current_admin() or "未登录")}</strong>
      {logout}
    </div>
  </aside>
  <main class="main">
    <header class="topbar">
      <div><p>SuperToyBox Platform</p><h1>{h(title)}</h1></div>
      <div class="top-actions"><span class="search-pill">统一更新 · 激活授权</span><span class="avatar">{h((current_admin() or "A")[:1].upper())}</span></div>
    </header>
    {messages}
    {body}
  </main>
</body>
</html>"""


@app.get("/")
def index():
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        device_seen_rows = conn.execute("SELECT last_seen_at FROM devices").fetchall()
        stats = {
            "releases": conn.execute("SELECT COUNT(*) AS c FROM releases").fetchone()["c"],
            "licenses": conn.execute("SELECT COUNT(*) AS c FROM license_codes").fetchone()["c"],
            "activated": conn.execute("SELECT COUNT(*) AS c FROM license_codes WHERE device_code != '' AND enabled = 1").fetchone()["c"],
            "devices": conn.execute("SELECT COUNT(*) AS c FROM devices").fetchone()["c"],
            "online": sum(1 for row in device_seen_rows if is_recent_online(row["last_seen_at"])),
        }
        latest = latest_release()
        logs = conn.execute("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 8").fetchall()
        devices = conn.execute("SELECT * FROM devices ORDER BY last_seen_at DESC LIMIT 6").fetchall()
        releases = conn.execute("SELECT * FROM releases ORDER BY id DESC LIMIT 5").fetchall()
    latest_text = f"v{latest['version']} · 版本码 {latest['version_code']}" if latest else "暂无版本"
    latest_note = h(latest["release_notes"][:120]) if latest and latest["release_notes"] else "等待发布第一个正式版本"
    release_rows = "".join(
        f'<div class="data-row"><span>v{h(row["version"])}</span><span>{h(row["version_code"])}</span><span>{format_size(row["apk_size"])}</span><span>{status_badge("启用" if row["enabled"] else "停用", "green" if row["enabled"] else "gray")}</span></div>'
        for row in releases
    ) or '<p class="empty">暂无版本记录</p>'
    device_rows = "".join(
        f'<div class="data-row"><span class="mono">{h(item["device_code"])}</span><span>{h(item["app_version"] or "-")}</span><span>{status_badge("在线" if is_recent_online(item["last_seen_at"]) else "离线", "green" if is_recent_online(item["last_seen_at"]) else "gray")}</span><span>{h(item["last_seen_at"])}</span></div>'
        for item in devices
    ) or '<p class="empty">暂无车辆记录</p>'
    log_items = "".join(
        f'<div class="timeline-item"><span></span><div><b>{h(item["type"])}</b><p>{h(item["message"])}</p><small>{h(item["created_at"])}</small></div></div>'
        for item in logs
    ) or '<p class="empty">暂无日志</p>'
    body = f"""
<section class="hero-grid">
  <article class="hero-card">
    <div class="eyebrow">当前推送</div>
    <h2>{h(latest_text)}</h2>
    <p>{latest_note}</p>
    <div class="hero-actions"><a class="button primary" href="{h(url_for("updates"))}">发布新版</a></div>
  </article>
  <article class="license-card">
    <span>{status_badge("商业版", "red")}</span>
    <h3>{stats["activated"]}/{stats["licenses"]}</h3>
    <p>永久激活 / 激活码总量</p>
    <small>按真实车架号 VIN 绑定授权</small>
  </article>
</section>
<section class="metric-grid three">
  <article class="metric"><span>版本数量</span><b>{stats["releases"]}</b><small>统一更新源</small></article>
  <article class="metric"><span>车辆记录</span><b>{stats["devices"]}</b><small>{stats["online"]} 台最近在线</small></article>
  <article class="metric"><span>授权转化</span><b>{stats["activated"]}</b><small>已绑定 VIN</small></article>
</section>
<section class="dashboard-grid">
  <article class="panel"><div class="panel-title"><div><h2>版本动态</h2><p>最新发布与推送状态</p></div><a href="{h(url_for("updates"))}">查看全部</a></div><div class="data-table four">{release_rows}</div></article>
  <article class="panel"><div class="panel-title"><div><h2>车辆在线</h2><p>最近连接到平台的车辆</p></div><a href="{h(url_for("vehicles"))}">车辆列表</a></div><div class="data-table four">{device_rows}</div></article>
  <article class="panel"><div class="panel-title"><div><h2>系统日志</h2><p>后台操作与接口事件</p></div><a href="{h(url_for("settings"))}">更多</a></div><div class="timeline">{log_items}</div></article>
</section>
"""
    return page("总览", "dashboard", body)


@app.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        key = client_key()
        with db() as conn:
            remaining = login_lock_remaining(conn, key)
            if remaining > 0:
                flash(f"请求过于频繁，请 {remaining} 秒后再试", "error")
                return redirect(url_for("login"))
            admin = conn.execute("SELECT * FROM admins WHERE id = 1").fetchone()
            if admin and username == admin["username"] and check_password_hash(admin["password_hash"], password):
                session.permanent = True
                session["admin"] = username
                record_login_success(conn, key)
                log_event(conn, "system", f"管理员登录成功：{username}", key)
                return redirect(safe_next_target(request.args.get("next")) or url_for("index"))
            remaining = record_login_failure(conn, key)
            log_event(conn, "security", f"管理员登录失败：{username or '空账号'}", key)
        if remaining > 0:
            flash(f"账号或密码错误，请 {remaining} 秒后再试", "error")
        else:
            flash("账号或密码错误", "error")
    body = f"""
<section class="auth panel">
  <h2>管理员登录</h2>
  <form method="post" class="form">
    {csrf_input()}
    <label>账号<input name="username" autocomplete="username" required></label>
    <label>密码<input name="password" type="password" autocomplete="current-password" required></label>
    <button class="primary" type="submit">登录</button>
  </form>
</section>
"""
    return page("登录", "dashboard", body)


@app.post("/logout")
def logout():
    session.clear()
    return redirect(url_for("login"))


@app.route("/updates", methods=["GET", "POST"])
def updates():
    guard = require_login()
    if guard:
        return guard
    if request.method == "POST":
        version = request.form.get("version", "").strip().lstrip("v")
        version_code_raw = request.form.get("version_code", "").strip()
        notes = request.form.get("release_notes", "").strip()
        force = 1 if request.form.get("force_update") == "on" else 0
        file = request.files.get("apk")
        if not version or not version_code_raw:
            flash("请填写版本号和版本码", "error")
            return redirect(url_for("updates"))
        try:
            version_code = int(version_code_raw)
        except ValueError:
            flash("版本码必须是数字", "error")
            return redirect(url_for("updates"))
        if file is None or not file.filename.lower().endswith(".apk"):
            flash("请上传 APK 文件", "error")
            return redirect(url_for("updates"))
        safe_name = secure_filename(file.filename) or f"SuperToyBox-{version}.apk"
        filename = f"SuperToyBox-v{version}-{int(utc_datetime().timestamp())}-{safe_name}"
        target = APK_DIR / filename
        try:
            file.save(target)
            metadata = inspect_apk(target)
            if metadata.version_name != version:
                raise ApkValidationError(
                    f"表单版本号 {version} 与 APK 版本号 {metadata.version_name} 不一致"
                )
            if metadata.version_code != version_code:
                raise ApkValidationError(
                    f"表单版本码 {version_code} 与 APK 版本码 {metadata.version_code} 不一致"
                )
            target.chmod(0o640)
        except ApkValidationError as exc:
            target.unlink(missing_ok=True)
            flash(str(exc), "error")
            return redirect(url_for("updates"))
        except Exception:
            target.unlink(missing_ok=True)
            raise
        now = utc_now()
        with db() as conn:
            conn.execute(
                """
                INSERT INTO releases
                    (version, version_code, apk_filename, apk_size, apk_sha256,
                     package_name, certificate_sha256, release_notes, force_update,
                     enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                """,
                (
                    version,
                    version_code,
                    filename,
                    target.stat().st_size,
                    metadata.sha256,
                    metadata.package_name,
                    metadata.certificate_sha256,
                    notes,
                    force,
                    now,
                    now,
                ),
            )
            log_event(conn, "update", f"发布版本 v{version}")
        flash(f"版本 v{version} 已发布", "success")
        return redirect(url_for("updates"))
    with db() as conn:
        rows = conn.execute("SELECT * FROM releases ORDER BY id DESC LIMIT 80").fetchall()
    csrf = csrf_input()
    latest = rows[0] if rows else None
    history_rows = "".join(
        f"""
<div class="data-row release-row">
  <span><b>v{h(row["version"])}</b><small>{h(row["updated_at"])}</small></span>
  <span>{h(row["version_code"])}</span>
  <span>{format_size(row["apk_size"])}</span>
  <span>{status_badge("强制" if row["force_update"] else "普通", "orange" if row["force_update"] else "blue")} {status_badge("启用" if row["enabled"] else "停用", "green" if row["enabled"] else "gray")}</span>
  <span class="actions"><a class="button soft" href="{h(url_for("download_apk", filename=row["apk_filename"]))}">下载</a>
  <form method="post" action="{h(url_for("toggle_release", release_id=row["id"]))}">{csrf}<button>{"停用" if row["enabled"] else "启用"}</button></form>
  <form method="post" action="{h(url_for("delete_release", release_id=row["id"]))}" onsubmit="return confirm('确定删除这个版本吗？')">{csrf}<button class="danger">删除</button></form></span>
</div>"""
        for row in rows
    ) or '<p class="empty">暂无历史版本</p>'
    body = f"""
<section class="page-grid">
  <article class="panel primary-panel">
    <div class="panel-title"><div><h2>发布新版</h2><p>上传 APK 后，客户端只从统一更新源读取版本。</p></div>{status_badge("当前 " + ("v" + latest["version"] if latest else "无版本"), "red")}</div>
    <form method="post" enctype="multipart/form-data" class="form">
      {csrf}
      <div class="two"><label>版本号<input name="version" placeholder="3.2.29" required></label><label>版本码<input name="version_code" type="number" min="1" required></label></div>
      <label>APK 文件<input name="apk" type="file" accept=".apk,application/vnd.android.package-archive" required></label>
      <label>更新说明<textarea name="release_notes" rows="5" placeholder="写给用户看的更新内容"></textarea></label>
      <label class="check"><input name="force_update" type="checkbox">强制更新</label>
      <button class="primary" type="submit">发布更新</button>
    </form>
  </article>
  <article class="panel stat-panel">
    <h2>更新源</h2>
    <p class="endpoint">/api/v1/update/latest</p>
    <small>客户端使用这一条统一更新接口。</small>
  </article>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>历史版本</h2><p>管理启用状态、强制更新和 APK 下载。</p></div></div>
  <div class="data-table release-table">
    <div class="data-row table-head"><span>版本</span><span>版本码</span><span>大小</span><span>状态</span><span>操作</span></div>
    {history_rows}
  </div>
</section>
"""
    return page("更新管理", "updates", body)


@app.post("/updates/<int:release_id>/toggle")
def toggle_release(release_id):
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        row = conn.execute("SELECT enabled FROM releases WHERE id = ?", (release_id,)).fetchone()
        if row is None:
            abort(404)
        conn.execute("UPDATE releases SET enabled = ?, updated_at = ? WHERE id = ?", (0 if row["enabled"] else 1, utc_now(), release_id))
        log_event(conn, "update", f"切换版本 #{release_id} 状态")
    return redirect(url_for("updates"))


@app.post("/updates/<int:release_id>/delete")
def delete_release(release_id):
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        row = conn.execute("SELECT apk_filename FROM releases WHERE id = ?", (release_id,)).fetchone()
        if row is None:
            abort(404)
        conn.execute("DELETE FROM releases WHERE id = ?", (release_id,))
        log_event(conn, "update", f"删除版本 #{release_id}")
    path = APK_DIR / row["apk_filename"]
    if path.exists():
        path.unlink()
    return redirect(url_for("updates"))


@app.route("/service-qrcodes", methods=["GET", "POST"])
def service_qrcodes():
    guard = require_login()
    if guard:
        return guard
    if request.method == "POST":
        key = request.form.get("key", "").strip()
        if key not in SERVICE_QR_TYPES:
            flash("二维码类型无效", "error")
            return redirect(url_for("service_qrcodes"))
        title, subtitle, default_description = SERVICE_QR_TYPES[key]
        description = request.form.get("description", "").strip() or default_description
        expires_at = request.form.get("expires_at", "").strip()
        enabled = 1 if request.form.get("enabled") == "on" else 0
        file = request.files.get("image")
        now = utc_now()
        new_filename = None
        original = ""
        mime_type = ""
        file_size = 0
        if file is not None and file.filename:
            if not allowed_image_file(file.filename):
                flash("二维码只支持 png、jpg、jpeg、webp", "error")
                return redirect(url_for("service_qrcodes"))
            original = file.filename
            safe_name = secure_filename(original) or f"{key}.png"
            suffix = safe_name.rsplit(".", 1)[-1].lower()
            new_filename = f"{key}-{utc_datetime().strftime('%Y%m%d%H%M%S')}-{secrets.token_hex(4)}.{suffix}"
            target = SERVICE_QR_DIR / new_filename
            file.save(target)
            file_size = target.stat().st_size
            if file_size <= 0 or file_size > MAX_SERVICE_QR_SIZE:
                target.unlink(missing_ok=True)
                flash(f"二维码图片不能超过 {format_size(MAX_SERVICE_QR_SIZE)}", "error")
                return redirect(url_for("service_qrcodes"))
            target.chmod(0o644)
            mime_type = file.mimetype or ""
        with db() as conn:
            row = conn.execute("SELECT * FROM service_qrcodes WHERE key = ?", (key,)).fetchone()
            old_filename = row["filename"] if row else ""
            if new_filename:
                conn.execute(
                    """
                    UPDATE service_qrcodes
                    SET title = ?, subtitle = ?, description = ?, filename = ?, original_filename = ?,
                        mime_type = ?, file_size = ?, enabled = ?, expires_at = ?, updated_at = ?
                    WHERE key = ?
                    """,
                    (title, subtitle, description, new_filename, original, mime_type, file_size, enabled, expires_at, now, key),
                )
            else:
                conn.execute(
                    """
                    UPDATE service_qrcodes
                    SET title = ?, subtitle = ?, description = ?, enabled = ?, expires_at = ?, updated_at = ?
                    WHERE key = ?
                    """,
                    (title, subtitle, description, enabled, expires_at, now, key),
                )
            log_event(conn, "service", f"更新服务二维码：{title}")
        if new_filename and old_filename and old_filename != new_filename:
            old_path = SERVICE_QR_DIR / old_filename
            if old_path.exists():
                old_path.unlink()
        flash(f"{title} 已更新，客户端会自动读取最新二维码", "success")
        return redirect(url_for("service_qrcodes"))

    qr_keys = list(SERVICE_QR_TYPES.keys())
    qr_order = " ".join(f"WHEN '{key}' THEN {index}" for index, key in enumerate(qr_keys, start=1))
    with db() as conn:
        rows = conn.execute(
            f"SELECT * FROM service_qrcodes WHERE key IN ({sql_placeholders(qr_keys)}) ORDER BY CASE key {qr_order} ELSE 9 END",
            qr_keys,
        ).fetchall()
    csrf = csrf_input()
    cards = "".join(
        f"""
<article class="qr-card">
  <div class="qr-preview">
    {f'<img src="{h(url_for("service_qr_image", key=row["key"]))}?v={h(epoch_millis(row["updated_at"]))}" alt="{h(row["title"])}">' if row["filename"] else '<span>未上传</span>'}
  </div>
  <form method="post" enctype="multipart/form-data" class="form qr-form">
    {csrf}
    <input type="hidden" name="key" value="{h(row["key"])}">
    <div class="panel-title compact"><div><h2>{h(row["title"])}</h2><p>{h(row["subtitle"])}</p></div>{status_badge("启用" if row["enabled"] else "停用", "green" if row["enabled"] else "gray")}</div>
    <label>二维码图片<input name="image" type="file" accept=".png,.jpg,.jpeg,.webp,image/*"></label>
    <label>说明<input name="description" value="{h(row["description"])}"></label>
    <label>过期时间<input name="expires_at" value="{h(row["expires_at"])}" placeholder="例如 2026-07-14 23:59，可不填"></label>
    <label class="check"><input name="enabled" type="checkbox" {"checked" if row["enabled"] else ""}>客户端显示这个二维码</label>
    <button class="primary" type="submit">保存并更新客户端</button>
    <small class="muted">最近更新：{h(row["updated_at"])} · {format_size(row["file_size"]) if row["filename"] else "暂无图片"}</small>
  </form>
</article>"""
        for row in rows
    )
    body = f"""
<section class="page-grid">
  <article class="panel primary-panel">
    <div class="panel-title"><div><h2>服务与授权二维码</h2><p>上传后客户端设置页会自动读取最新图片，微信群二维码 7 天失效时只需要在这里替换。</p></div>{status_badge("客户端实时读取", "green")}</div>
    <p class="endpoint">/api/v1/service/qrcodes</p>
    <small class="muted">建议图片使用正方形 PNG/JPG，单张不超过 {format_size(MAX_SERVICE_QR_SIZE)}。微信客服、付款二维码、微信交流群都在这里统一管理。</small>
  </article>
  <article class="panel stat-panel">
    <h2>客户端逻辑</h2>
    <p class="muted">有网络时读取最新二维码；没网络时继续显示上一次缓存图片；后台替换图片后接口会带新的更新时间，避免旧缓存。</p>
  </article>
</section>
<section class="qr-grid">{cards}</section>
"""
    return page("服务与授权", "service", body)


@app.route("/licenses", methods=["GET", "POST"])
def licenses():
    guard = require_login()
    if guard:
        return guard
    if request.method == "POST":
        try:
            count = int(request.form.get("count", "1") or "1")
        except (TypeError, ValueError):
            flash("生成数量必须是数字", "error")
            return redirect(url_for("licenses"))
        count = max(1, min(count, 100))
        note = request.form.get("note", "").strip()
        generated = []
        now = utc_now()
        with db() as conn:
            for _ in range(count):
                for _attempt in range(20):
                    code = generate_license_code()
                    try:
                        conn.execute(
                            "INSERT INTO license_codes (code, note, enabled, created_at, updated_at) VALUES (?, ?, 1, ?, ?)",
                            (code, note, now, now),
                        )
                        generated.append(code)
                        break
                    except sqlite3.IntegrityError:
                        continue
            log_event(conn, "license", f"生成 {len(generated)} 个激活码")
        flash("已生成：" + "  ".join(generated), "success")
        return redirect(url_for("licenses"))
    with db() as conn:
        rows = conn.execute("SELECT * FROM license_codes ORDER BY id DESC LIMIT 200").fetchall()
        devices = conn.execute("SELECT * FROM devices ORDER BY last_seen_at DESC LIMIT 80").fetchall()
        device_authorizations = {
            item["id"]: authorization_category(device_authorization(conn, item)) for item in devices
        }
        activated_count = conn.execute(
            "SELECT COUNT(*) AS c FROM license_codes WHERE enabled = 1 AND device_code != ''"
        ).fetchone()["c"]
        unused_count = conn.execute(
            "SELECT COUNT(*) AS c FROM license_codes WHERE enabled = 1 AND device_code = ''"
        ).fetchone()["c"]
        device_count = conn.execute("SELECT COUNT(*) AS c FROM devices").fetchone()["c"]
    csrf = csrf_input()
    license_rows = "".join(
        f"""
<div class="data-row license-row">
  <span class="code" onclick='navigator.clipboard.writeText({h(json.dumps(row["code"], ensure_ascii=False))})'>{h(row["code"])}</span>
  <span class="mono">{h(row["device_code"] or "未绑定")}</span>
  <span>{status_badge("已绑定" if row["device_code"] else "未使用", "green" if row["device_code"] else "blue")} {status_badge("启用" if row["enabled"] else "停用", "green" if row["enabled"] else "gray")}</span>
  <span>{h(row["note"] or "-")}</span>
  <span class="actions"><form method="post" action="{h(url_for("toggle_license", code_id=row["id"]))}">{csrf}<button>{"停用" if row["enabled"] else "启用"}</button></form>
  <form method="post" action="{h(url_for("delete_license", code_id=row["id"]))}" onsubmit="return confirm('确定删除这个激活码？')">{csrf}<button class="danger">删除</button></form></span>
</div>"""
        for row in rows
    ) or '<p class="empty">暂无激活码</p>'
    device_rows = "".join(
        f'<div class="data-row vehicle-row"><span class="mono">{h(item["device_code"])}</span><span>{h(item["app_version"] or "-")}</span><span>{status_badge(device_authorizations[item["id"]][1], device_authorizations[item["id"]][2])}</span><span>{h(item["last_seen_at"])}</span></div>'
        for item in devices
    ) or '<p class="empty">暂无车辆记录</p>'
    body = f"""
<section class="page-grid">
  <article class="panel primary-panel">
    <div class="panel-title"><div><h2>生成激活码</h2><p>激活码首次使用后绑定真实车架号 VIN，更新或重装可自动恢复授权。</p></div>{status_badge("VIN 绑定", "red")}</div>
    <form method="post" class="form">
      {csrf}
      <div class="two"><label>数量<input name="count" type="number" min="1" max="100" value="1"></label><label>备注<input name="note" placeholder="例如：首批用户"></label></div>
      <button class="primary" type="submit">生成激活码</button>
    </form>
  </article>
  <article class="panel stat-panel">
    <h2>授权概览</h2>
    <div class="mini-metrics"><span><b>{activated_count}</b><small>已绑定</small></span><span><b>{unused_count}</b><small>可使用</small></span><span><b>{device_count}</b><small>车辆记录</small></span></div>
  </article>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>激活码</h2><p>点击激活码可复制，一键停用或删除。</p></div></div>
  <div class="data-table license-table"><div class="data-row table-head"><span>激活码</span><span>绑定车架号</span><span>状态</span><span>备注</span><span>操作</span></div>{license_rows}</div>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>最近车辆</h2><p>查看授权、试用与最后在线时间。</p></div><a href="{h(url_for("vehicles"))}">全部车辆</a></div>
  <div class="data-table vehicle-table"><div class="data-row table-head"><span>车架号</span><span>版本</span><span>授权</span><span>最近在线</span></div>{device_rows}</div>
</section>
"""
    return page("激活授权", "licenses", body)


@app.post("/licenses/<int:code_id>/toggle")
def toggle_license(code_id):
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        row = conn.execute(
            "SELECT enabled, device_code FROM license_codes WHERE id = ?",
            (code_id,),
        ).fetchone()
        if row is None:
            abort(404)
        conn.execute("UPDATE license_codes SET enabled = ?, updated_at = ? WHERE id = ?", (0 if row["enabled"] else 1, utc_now(), code_id))
        refresh_cached_device_activation(conn, row["device_code"])
        log_event(conn, "license", f"切换激活码 #{code_id} 状态")
    return redirect(url_for("licenses"))


@app.post("/licenses/<int:code_id>/delete")
def delete_license(code_id):
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        row = conn.execute(
            "SELECT device_code FROM license_codes WHERE id = ?",
            (code_id,),
        ).fetchone()
        conn.execute("DELETE FROM license_codes WHERE id = ?", (code_id,))
        if row is not None:
            refresh_cached_device_activation(conn, row["device_code"])
        log_event(conn, "license", f"删除激活码 #{code_id}")
    return redirect(url_for("licenses"))


@app.get("/payments")
def payment_orders():
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        rows = conn.execute(
            "SELECT * FROM payment_orders ORDER BY id DESC LIMIT 300"
        ).fetchall()
        counts = {
            row["status"]: row["count"]
            for row in conn.execute(
                "SELECT status, COUNT(*) AS count FROM payment_orders GROUP BY status"
            ).fetchall()
        }
    status_labels = {
        "pending": ("待支付", "orange"),
        "paid": ("已支付", "green"),
        "failed": ("下单失败", "red"),
        "expired": ("已过期", "gray"),
    }
    order_rows = "".join(
        f"""
<div class="data-row payment-row">
  <span class="mono">{h(row["out_trade_no"])}</span>
  <span>{h("支付宝" if row["payment_type"] == "alipay" else "微信支付")}</span>
  <span>{int(row["expected_amount_cents"]) / 100:.2f} 元</span>
  <span class="mono">{h(row["device_code"])}</span>
  <span>{status_badge(*status_labels.get(row["status"], (row["status"], "gray")))}</span>
  <span>{h(row["paid_at"] or row["created_at"])}</span>
</div>"""
        for row in rows
    ) or '<p class="empty">暂无支付订单</p>'
    body = f"""
<section class="metric-grid three">
  <article class="metric"><span>待支付</span><b>{counts.get("pending", 0)}</b><small>等待用户扫码</small></article>
  <article class="metric"><span>已支付</span><b>{counts.get("paid", 0)}</b><small>已自动绑定授权</small></article>
  <article class="metric"><span>异常订单</span><b>{counts.get("failed", 0) + counts.get("expired", 0)}</b><small>下单失败或超时</small></article>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>易支付 V2 订单</h2><p>平台验签并主动查单后才会自动激活，客户端支付状态不能直接改变授权。</p></div>{status_badge("RSA 双向签名", "green")}</div>
  <div class="data-table payment-table"><div class="data-row table-head"><span>商户订单号</span><span>支付方式</span><span>金额</span><span>车架号</span><span>状态</span><span>时间</span></div>{order_rows}</div>
</section>
"""
    return page("支付订单", "payments", body)


@app.get("/vehicles")
def vehicles():
    guard = require_login()
    if guard:
        return guard
    requested_category = request.args.get("status", "activated").strip().lower()
    selected_category = (
        requested_category
        if requested_category in {"activated", "trial", "expired"}
        else "activated"
    )
    keyword = request.args.get("q", "").strip()
    conditions = []
    params = []
    if keyword:
        like = f"%{keyword}%"
        conditions.append("(device_code LIKE ? OR app_version LIKE ?)")
        params += [like, like]
    where = f"WHERE {' AND '.join(conditions)}" if conditions else ""
    with db() as conn:
        matched = conn.execute(
            f"SELECT * FROM devices {where} ORDER BY last_seen_at DESC",
            params,
        ).fetchall()
        categorized = [
            (item, authorization_category(device_authorization(conn, item))) for item in matched
        ]
    counts = {
        key: sum(1 for _, category in categorized if category[0] == key)
        for key in ("activated", "trial", "expired")
    }
    rows = [
        (item, category)
        for item, category in categorized
        if category[0] == selected_category
    ][:300]
    vehicle_rows = "".join(
        f"""
<div class="data-row vehicle-row">
  <span><a class="mono" href="{h(url_for("vehicle_detail", device_id=item["id"]))}"><b>{h(item["device_code"])}</b></a></span>
  <span>{status_badge(category[1], category[2])}</span>
  <span>{h(item["app_version"] or "-")}</span>
  <span>{h(item["last_seen_at"])}</span>
</div>"""
        for item, category in rows
    ) or '<p class="empty">该分类暂无车辆记录</p>'
    category_tabs = "".join(
        f'<a class="chip {"active" if key == selected_category else ""}" href="{h(url_for("vehicles", status=key, q=keyword))}">{label} <small>{counts[key]}</small></a>'
        for key, label in (
            ("activated", "已激活"),
            ("trial", "试用"),
            ("expired", "已到期"),
        )
    )
    body = f"""
<section class="panel">
  <div class="panel-title"><div><h2>车辆列表</h2><p>按授权状态查看已登记的车辆设备。</p></div><div class="chip-list">{category_tabs}</div></div>
  <form method="get" class="toolbar"><input type="hidden" name="status" value="{h(selected_category)}"><input name="q" value="{h(keyword)}" placeholder="搜索 VIN 或应用版本"><button>搜索</button></form>
  <div class="data-table vehicles-table detailed"><div class="data-row table-head"><span>VIN</span><span>授权</span><span>版本</span><span>最近连接</span></div>{vehicle_rows}</div>
</section>
"""
    return page("车辆设备", "vehicles", body)


@app.get("/vehicles/<int:device_id>")
def vehicle_detail(device_id):
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        device = conn.execute("SELECT * FROM devices WHERE id = ?", (device_id,)).fetchone()
        if device is None:
            abort(404)
        authorization = device_authorization(conn, device)
        logs = conn.execute(
            "SELECT * FROM diagnostic_logs WHERE device_code = ? ORDER BY id DESC LIMIT 20",
            (device["device_code"],),
        ).fetchall()
    auth_category = authorization_category(authorization)
    license_row = authorization["license"]
    csrf = csrf_input()
    credential_status = "已签发" if device["api_token_hash"] else "未签发"
    log_rows = "".join(
        f'<div class="data-row"><span>#{row["id"]}</span><span>{h(row["created_at"])}</span>'
        f'<span>{format_size(row["file_size"])}</span><span><a class="button soft" href="{h(url_for("view_diagnostic_log", log_id=row["id"]))}" target="_blank">查看</a></span></div>'
        for row in logs
    ) or '<p class="empty">该车暂无上传日志</p>'
    body = f"""
<section class="metric-grid">
  <article class="metric"><span>授权</span><b>{h(auth_category[1])}</b><small>{h((license_row["code"] + " · " + (license_row["activated_at"] or "未记录激活时间")) if license_row else (device["trial_ends_at"] or "无激活码"))}</small></article>
  <article class="metric"><span>应用版本</span><b>{h(device["app_version"] or "-")}</b><small>{h(device["last_seen_at"])}</small></article>
  <article class="metric"><span>应用包名</span><b>{h(device["package_name"] or "-")}</b><small>车辆端身份</small></article>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>车辆身份</h2><p class="mono">VIN {h(device["device_code"])}</p></div></div>
  <div class="detail-grid">
    <span><small>首次登记</small><b>{h(device["first_seen_at"])}</b></span>
    <span><small>最近连接</small><b>{h(device["last_seen_at"])}</b></span>
    <span><small>应用版本</small><b>{h(device["app_version"] or "-")}</b></span>
    <span><small>应用包名</small><b>{h(device["package_name"] or "-")}</b></span>
    <span><small>设备凭证</small><b>{h(credential_status)}</b></span>
  </div>
  <form method="post" action="{h(url_for("reset_vehicle_credential", device_id=device["id"]))}" onsubmit="return confirm('确定重置这台车的设备凭证吗？之后只能通过激活码、已支付订单或签名离线许可证重新签发。')">
    {csrf}
    <button class="danger">重置设备凭证</button>
  </form>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>最近诊断日志</h2><p>查看该车主动上传的 wjx.log。</p></div></div>
  <div class="data-table logs-table">{log_rows}</div>
</section>
"""
    return page("车辆详情", "vehicles", body)


@app.post("/vehicles/<int:device_id>/reset-credential")
def reset_vehicle_credential(device_id):
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        device = conn.execute("SELECT * FROM devices WHERE id = ?", (device_id,)).fetchone()
        if device is None:
            abort(404)
        cursor = conn.execute(
            "UPDATE devices SET api_token_hash = '', api_token_issued_at = NULL WHERE id = ?",
            (device_id,),
        )
        log_event(
            conn,
            "credential",
            f"管理员重置车辆 {device['device_code']} 的设备凭证，共 {cursor.rowcount} 条关联记录",
        )
    flash("设备凭证已重置；车辆端需凭激活码、支付订单或签名离线许可证重新签发", "success")
    return redirect(url_for("vehicle_detail", device_id=device_id))


@app.get("/diagnostic-logs")
def diagnostic_logs():
    guard = require_login()
    if guard:
        return guard
    keyword = request.args.get("q", "").strip()
    conditions = []
    params = []
    if keyword:
        like = f"%{keyword}%"
        conditions.append(
            "(device_code LIKE ? OR app_version LIKE ? OR package_name LIKE ? OR note LIKE ? OR remote_addr LIKE ?)"
        )
        params += [like, like, like, like, like]
    where = f"WHERE {' AND '.join(conditions)}" if conditions else ""
    with db() as conn:
        rows = conn.execute(
            f"SELECT * FROM diagnostic_logs {where} ORDER BY id DESC LIMIT 300",
            params,
        ).fetchall()
        summary = conn.execute(
            f"SELECT COUNT(*) AS total, COALESCE(SUM(file_size), 0) AS bytes FROM diagnostic_logs {where}",
            params,
        ).fetchone()
    csrf = csrf_input()
    log_rows = "".join(
        f"""
<div class="data-row diagnostic-row">
  <span><b>#{row["id"]}</b><small>{h(row["created_at"])}</small></span>
  <span class="mono">{h(row["device_code"])}<small>v{h(row["app_version"] or "-")}</small></span>
  <span>{format_size(row["file_size"])}</span>
  <span>{h(row["remote_addr"] or "-")}</span>
  <span>{h(row["note"] or "无备注")}</span>
  <span class="actions">
    <a class="button soft" target="_blank" rel="noopener" href="{h(url_for("view_diagnostic_log", log_id=row["id"]))}">查看</a>
    <a class="button soft" href="{h(url_for("download_diagnostic_log", log_id=row["id"]))}">下载</a>
    <form method="post" action="{h(url_for("delete_diagnostic_log", log_id=row["id"]))}" onsubmit="return confirm('确定删除这份诊断日志吗？')">{csrf}<button class="danger">删除</button></form>
  </span>
</div>"""
        for row in rows
    ) or '<p class="empty">暂无诊断日志</p>'
    body = f"""
<section class="metric-grid three">
  <article class="metric"><span>日志数量</span><b>{summary["total"]}</b><small>当前查询结果</small></article>
  <article class="metric"><span>占用空间</span><b>{format_size(summary["bytes"])}</b><small>单份最多 {MAX_LOG_SIZE // 1024 // 1024} MB</small></article>
  <article class="metric"><span>上传限制</span><b>{LOG_UPLOAD_DEVICE_LIMIT} 次</b><small>每车每小时</small></article>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>车辆诊断日志</h2><p>按车架号、版本、备注或来源地址查找客户端上传的日志。</p></div></div>
  <form method="get" class="toolbar"><input name="q" value="{h(keyword)}" placeholder="搜索车架号、版本、备注、IP"><button>搜索</button></form>
  <div class="data-table diagnostic-table">
    <div class="data-row table-head"><span>编号 / 时间</span><span>车辆 / 版本</span><span>大小</span><span>来源</span><span>备注</span><span>操作</span></div>
    {log_rows}
  </div>
</section>
"""
    return page("诊断日志", "diagnostic_logs", body)


def diagnostic_log_row(log_id):
    with db() as conn:
        return conn.execute("SELECT * FROM diagnostic_logs WHERE id = ?", (log_id,)).fetchone()


@app.get("/diagnostic-logs/<int:log_id>/view")
def view_diagnostic_log(log_id):
    guard = require_login()
    if guard:
        return guard
    row = diagnostic_log_row(log_id)
    if row is None:
        abort(404)
    return send_from_directory(
        LOG_DIR,
        Path(row["filename"]).name,
        as_attachment=False,
        mimetype="text/plain; charset=utf-8",
    )


@app.get("/diagnostic-logs/<int:log_id>/download")
def download_diagnostic_log(log_id):
    guard = require_login()
    if guard:
        return guard
    row = diagnostic_log_row(log_id)
    if row is None:
        abort(404)
    download_name = f"wjx-{row['device_code']}-{row['id']}.log"
    return send_from_directory(
        LOG_DIR,
        Path(row["filename"]).name,
        as_attachment=True,
        download_name=download_name,
        mimetype="text/plain; charset=utf-8",
    )


@app.post("/diagnostic-logs/<int:log_id>/delete")
def delete_diagnostic_log(log_id):
    guard = require_login()
    if guard:
        return guard
    with db() as conn:
        row = conn.execute("SELECT * FROM diagnostic_logs WHERE id = ?", (log_id,)).fetchone()
        if row is None:
            abort(404)
        conn.execute("DELETE FROM diagnostic_logs WHERE id = ?", (log_id,))
        log_event(conn, "diagnostic", f"删除诊断日志 #{log_id} {row['device_code']}")
    (LOG_DIR / Path(row["filename"]).name).unlink(missing_ok=True)
    flash(f"诊断日志 #{log_id} 已删除", "success")
    return redirect(url_for("diagnostic_logs"))


@app.route("/settings", methods=["GET", "POST"])
def settings():
    guard = require_login()
    if guard:
        return guard
    if request.method == "POST":
        old_password = request.form.get("old_password", "")
        new_password = request.form.get("new_password", "")
        if len(new_password) < 8:
            flash("新密码至少 8 位", "error")
            return redirect(url_for("settings"))
        with db() as conn:
            admin = conn.execute("SELECT * FROM admins WHERE id = 1").fetchone()
            if not admin or not check_password_hash(admin["password_hash"], old_password):
                flash("原密码错误", "error")
                return redirect(url_for("settings"))
            conn.execute("UPDATE admins SET password_hash = ?, updated_at = ? WHERE id = 1", (generate_password_hash(new_password, method=PASSWORD_HASH_METHOD), utc_now()))
            log_event(conn, "system", "管理员密码已修改")
        flash("管理员密码已修改", "success")
        return redirect(url_for("settings"))
    with db() as conn:
        logs = conn.execute("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 120").fetchall()
        cfg = {key: config_get(conn, key, value) for key, value in DEFAULT_CONFIG.items()}
    offline_key_ok, offline_key_detail = offline_license_private_key_status()
    csrf = csrf_input()
    checked = lambda key: "checked" if cfg.get(key) == "1" else ""
    offline_key_badge = status_badge("已配置", "green") if offline_key_ok else status_badge("未配置", "red")
    log_rows = "".join(
        f'<div class="data-row log-row"><span>{h(item["created_at"])}</span><span>{status_badge(item["type"], "blue")}</span><span>{h(item["message"])}</span><span>{h(item["remote_addr"])}</span></div>'
        for item in logs
    ) or '<p class="empty">暂无日志</p>'
    body = f"""
<section class="page-grid">
  <article class="panel">
    <div class="panel-title"><div><h2>管理员密码</h2><p>建议定期更换后台密码，避免外泄。</p></div>{status_badge("安全", "green")}</div>
    <form method="post" class="form">
      {csrf}
      <label>原密码<input name="old_password" type="password" required></label>
      <label>新密码<input name="new_password" type="password" minlength="8" required></label>
      <button class="primary" type="submit">保存密码</button>
    </form>
  </article>
  <article class="panel">
    <div class="panel-title"><div><h2>数据库备份</h2><p>下载 SQLite 数据库，包含版本、激活码和车辆记录。</p></div></div>
    <a class="button soft big-action" href="{h(url_for("download_backup"))}">下载备份</a>
  </article>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>配置中心</h2><p>控制客户端试用期、付费功能和在线服务开关。</p></div></div>
  <form method="post" action="{h(url_for("save_config"))}" class="settings-grid">
    {csrf}
    <label class="setting-card"><span>试用天数</span><input name="license_trial_days" type="number" min="0" max="30" value="{h(cfg.get("license_trial_days", "7"))}"><small>未激活用户的全功能试用期</small></label>
    <div class="setting-card"><span>离线授权私钥</span>{offline_key_badge}<small>{h(offline_key_detail)}</small></div>
    <label class="setting-card toggle"><input name="feature_automation_unlimited" type="checkbox" {checked("feature_automation_unlimited")}><span>自动化高级功能</span><small>超过免费规则数后需要授权</small></label>
    <label class="setting-card toggle"><input name="feature_cluster_projection" type="checkbox" {checked("feature_cluster_projection")}><span>按键映射到仪表盘</span><small>授权功能开关</small></label>
    <label class="setting-card toggle"><input name="update_enabled" type="checkbox" {checked("update_enabled")}><span>更新推送</span><small>关闭后客户端显示无新版本</small></label>
    <button class="primary span-all" type="submit">保存配置</button>
  </form>
</section>
<section class="panel">
  <div class="panel-title"><div><h2>系统日志</h2><p>最近 120 条后台操作与接口事件。</p></div></div>
  <div class="data-table logs-table"><div class="data-row table-head"><span>时间</span><span>类型</span><span>内容</span><span>来源</span></div>{log_rows}</div>
</section>
"""
    return page("系统设置", "settings", body)


@app.post("/settings/config")
def save_config():
    guard = require_login()
    if guard:
        return guard
    try:
        trial_days = max(0, min(int(request.form.get("license_trial_days", "7") or "7"), 30))
    except ValueError:
        trial_days = DEFAULT_TRIAL_DAYS
    with db() as conn:
        config_set(conn, "license_trial_days", str(trial_days))
        for key in (
            "feature_automation_unlimited",
            "feature_cluster_projection",
            "update_enabled",
        ):
            config_set(conn, key, "1" if request.form.get(key) == "on" else "0")
        log_event(conn, "system", "配置中心已更新")
    flash("配置已保存", "success")
    return redirect(url_for("settings"))


@app.get("/backup")
def download_backup():
    guard = require_login()
    if guard:
        return guard
    backup_path = create_database_backup()

    @after_this_request
    def remove_backup(response):
        backup_path.unlink(missing_ok=True)
        return response

    return send_file(
        backup_path,
        as_attachment=True,
        download_name=f"supertoybox-{utc_datetime().strftime('%Y%m%d%H%M%S')}.db",
    )


@app.get("/api/v1/update/latest")
def api_latest_update():
    with db() as conn:
        if not config_bool(conn, "update_enabled", True):
            return jsonify({"enabled": False})
    row = latest_release()
    if row is None:
        return jsonify({"enabled": False})
    return jsonify(
        {
            "enabled": bool(row["enabled"]),
            "version": row["version"],
            "versionCode": row["version_code"],
            "downloadUrl": public_url(f"/cdn/apk/{row['apk_filename']}"),
            "releaseNotes": row["release_notes"],
            "forceUpdate": bool(row["force_update"]),
            "apkSize": row["apk_size"],
            "sha256": row["apk_sha256"],
            "packageName": row["package_name"],
            "certificateSha256": row["certificate_sha256"],
            "updatedAt": row["updated_at"],
        }
    )


@app.post("/api/v1/logs/upload")
def api_upload_log():
    device_code = extract_valid_vin(request.form.get("deviceCode", ""))
    attempt_key = api_attempt_key("log_upload", device_code)
    app_version = request.form.get("appVersion", "").strip()[:64]
    package_name = request.form.get("packageName", "").strip()[:160]
    note = request.form.get("note", "").replace("\x00", "").strip()[:500]
    upload = request.files.get("log")

    with db() as conn:
        remaining = api_lock_remaining(conn, attempt_key)
        if remaining > 0:
            return jsonify({"success": False, "message": f"请求过于频繁，请 {remaining} 秒后再试"}), 429

        def reject(message, status=400):
            lock_remaining = record_api_failure(conn, attempt_key)
            if lock_remaining > 0:
                return jsonify({"success": False, "message": f"请求过于频繁，请 {lock_remaining} 秒后再试"}), 429
            return jsonify({"success": False, "message": message}), status

        if not device_code:
            return reject("车架号格式不正确")
        device = conn.execute(
            "SELECT * FROM devices WHERE device_code = ?",
            (device_code,),
        ).fetchone()
        if device is None:
            return reject("车辆尚未在平台登记，请先刷新授权状态", 403)
        if not device_token_matches(device, request_device_token()):
            return reject("设备凭证无效，请刷新授权状态", 401)
        if upload is None or not upload.filename:
            return reject("请选择日志文件")
        if not allowed_log_file(upload.filename):
            return reject("只支持 .log 或 .txt 日志")
        if request.content_length and request.content_length > MAX_LOG_SIZE + 256 * 1024:
            return reject(f"日志不能超过 {MAX_LOG_SIZE // 1024 // 1024} MB", 413)

        window_start = (utc_datetime() - timedelta(hours=1)).replace(microsecond=0).isoformat() + "Z"
        device_uploads = conn.execute(
            "SELECT COUNT(*) AS c FROM diagnostic_logs WHERE device_code = ? AND created_at >= ?",
            (device_code, window_start),
        ).fetchone()["c"]
        ip_uploads = conn.execute(
            "SELECT COUNT(*) AS c FROM diagnostic_logs WHERE remote_addr = ? AND created_at >= ?",
            (request.remote_addr or "", window_start),
        ).fetchone()["c"]
        if device_uploads >= max(1, LOG_UPLOAD_DEVICE_LIMIT) or ip_uploads >= max(1, LOG_UPLOAD_IP_LIMIT):
            return jsonify({"success": False, "message": "上传次数过多，请一小时后再试"}), 429

        suffix = upload.filename.rsplit(".", 1)[-1].lower()
        filename = f"{utc_datetime().strftime('%Y%m%d%H%M%S')}-{secrets.token_hex(12)}.{suffix}"
        target = LOG_DIR / filename
        try:
            file_size = save_limited_log(upload, target, MAX_LOG_SIZE)
            now = utc_now()
            cursor = conn.execute(
                """
                INSERT INTO diagnostic_logs
                    (device_code, app_version, package_name, note, filename, original_filename,
                     mime_type, file_size, remote_addr, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    device_code,
                    app_version,
                    package_name,
                    note,
                    filename,
                    Path(upload.filename).name[:255],
                    (upload.mimetype or "application/octet-stream")[:120],
                    file_size,
                    request.remote_addr or "",
                    now,
                ),
            )
            log_id = cursor.lastrowid
            conn.execute(
                "UPDATE devices SET app_version = ?, package_name = ?, last_seen_at = ? WHERE device_code = ?",
                (app_version or device["app_version"], package_name or device["package_name"], now, device_code),
            )
            record_api_success(conn, attempt_key)
            log_event(conn, "diagnostic", f"车辆 {device_code} 上传诊断日志 #{log_id}")
        except ValueError as error:
            return reject(str(error), 413 if "超过" in str(error) else 400)
        except Exception:
            target.unlink(missing_ok=True)
            raise
    return jsonify({"success": True, "logId": log_id, "message": "诊断日志已上传"})


@app.get("/cdn/apk/<path:filename>")
def download_apk(filename):
    return send_from_directory(APK_DIR, filename, as_attachment=False, mimetype="application/vnd.android.package-archive")


@app.get("/api/v1/service/qrcodes")
def api_service_qrcodes():
    qr_keys = list(SERVICE_QR_TYPES.keys())
    qr_order = " ".join(f"WHEN '{key}' THEN {index}" for index, key in enumerate(qr_keys, start=1))
    with db() as conn:
        rows = conn.execute(
            f"""
            SELECT * FROM service_qrcodes
            WHERE enabled = 1 AND key IN ({sql_placeholders(qr_keys)})
            ORDER BY CASE key {qr_order} ELSE 9 END
            """,
            qr_keys,
        ).fetchall()
    return jsonify({"items": [service_qr_to_api(row) for row in rows]})


@app.get("/cdn/service-qrcodes/<key>")
def service_qr_image(key):
    if key not in SERVICE_QR_TYPES:
        abort(404)
    with db() as conn:
        row = conn.execute("SELECT * FROM service_qrcodes WHERE key = ?", (key,)).fetchone()
    if row is None or not row["enabled"] or not row["filename"]:
        abort(404)
    return send_from_directory(SERVICE_QR_DIR, row["filename"], as_attachment=False)


@app.get("/api/v1/config")
def api_config():
    with db() as conn:
        values = {key: config_get(conn, key, value) for key, value in DEFAULT_CONFIG.items()}
    return jsonify(
        {
            "success": True,
            "trialDays": int(values.get("license_trial_days") or DEFAULT_TRIAL_DAYS),
            "features": {
                "automationUnlimited": values.get("feature_automation_unlimited") == "1",
                "clusterProjection": values.get("feature_cluster_projection") == "1",
                "update": values.get("update_enabled") == "1",
            },
        }
    )


def first_device_for_codes(conn, device_codes):
    if not device_codes:
        return None
    return conn.execute(
        f"""
        SELECT * FROM devices
        WHERE device_code IN ({sql_placeholders(device_codes)})
        ORDER BY COALESCE(trial_started_at, first_seen_at) ASC, id ASC
        LIMIT 1
        """,
        device_codes,
    ).fetchone()


def license_for_device_codes(conn, device_codes):
    if not device_codes:
        return None
    return conn.execute(
        f"""
        SELECT * FROM license_codes
        WHERE enabled = 1 AND device_code IN ({sql_placeholders(device_codes)})
        ORDER BY activated_at DESC, id DESC
        LIMIT 1
        """,
        device_codes,
    ).fetchone()


def trial_anchor_for_codes(conn, device_codes):
    existing = first_device_for_codes(conn, device_codes)
    if existing is not None:
        trial_days = config_int(conn, "license_trial_days", DEFAULT_TRIAL_DAYS)
        trial_started = existing["trial_started_at"] or existing["first_seen_at"]
        configured_trial_ends = add_days_from_iso(trial_started, trial_days)
        existing_trial_ends = existing["trial_ends_at"] or configured_trial_ends
        trial_ends = existing_trial_ends
        if license_for_device_codes(conn, device_codes) is None:
            existing_end = parse_utc(existing_trial_ends)
            configured_end = parse_utc(configured_trial_ends)
            if existing_end is None or (configured_end is not None and configured_end > existing_end):
                trial_ends = configured_trial_ends
        return {
            "trial_started_at": trial_started,
            "trial_ends_at": trial_ends,
        }
    now = utc_now()
    trial_days = config_int(conn, "license_trial_days", DEFAULT_TRIAL_DAYS)
    return {
        "trial_started_at": now,
        "trial_ends_at": add_days_iso(trial_days),
    }


def upsert_device(conn, device_code, raw_device_code, app_version, package_name, activated, trial_anchor=None):
    now = utc_now()
    anchor = trial_anchor or trial_anchor_for_codes(conn, [device_code])
    trial_started = anchor.get("trial_started_at") or now
    trial_ends = anchor.get("trial_ends_at") or add_days_iso(config_int(conn, "license_trial_days", DEFAULT_TRIAL_DAYS))
    conn.execute(
        """
        INSERT INTO devices
            (device_code, raw_device_code, app_version, package_name, activated, trial_started_at, trial_ends_at, first_seen_at, last_seen_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(device_code) DO UPDATE SET
            raw_device_code = excluded.raw_device_code,
            app_version = excluded.app_version,
            package_name = excluded.package_name,
            activated = excluded.activated,
            trial_started_at = COALESCE(devices.trial_started_at, excluded.trial_started_at),
            trial_ends_at = CASE
                WHEN devices.trial_ends_at IS NULL OR devices.trial_ends_at = '' THEN excluded.trial_ends_at
                WHEN excluded.trial_ends_at IS NOT NULL AND excluded.trial_ends_at > devices.trial_ends_at THEN excluded.trial_ends_at
                ELSE devices.trial_ends_at
            END,
            last_seen_at = excluded.last_seen_at
        """,
        (device_code, raw_device_code, app_version, package_name, 1 if activated else 0, trial_started, trial_ends, now, now),
    )


def upsert_device_group(conn, device_codes, raw_device_code, app_version, package_name, activated):
    codes = device_codes or []
    if not codes:
        return None
    anchor = trial_anchor_for_codes(conn, codes)
    for code in codes:
        upsert_device(conn, code, raw_device_code, app_version, package_name, activated, anchor)
    return first_device_for_codes(conn, codes)


def device_license_payload(
    conn,
    device_code,
    raw_device_code,
    app_version,
    package_name,
    activated,
    device_codes=None,
    credential_requested=False,
    presented_token="",
    credential_reissue_authorized=False,
    rotate_credential=False,
):
    codes = [device_code]
    device = upsert_device_group(conn, codes, raw_device_code, app_version, package_name, activated)
    if device is None:
        upsert_device(conn, device_code, raw_device_code, app_version, package_name, activated)
        device = conn.execute("SELECT * FROM devices WHERE device_code = ?", (device_code,)).fetchone()
    trial_ends_at = device["trial_ends_at"] if device else ""
    trial_end = parse_utc(trial_ends_at)
    trial_active = bool(trial_end and trial_end > utc_datetime())
    result = {
        "success": True,
        "activated": activated,
        "deviceCode": device_code,
        "trialActive": False if activated else trial_active,
        "trialExpired": False if activated else not trial_active,
        "trialEndsAt": epoch_millis(trial_ends_at),
        "trialEndsAtIso": trial_ends_at,
        "message": "已激活" if activated else ("试用中" if trial_active else "未激活"),
    }
    if credential_requested and activated:
        token = resolve_device_token(
            conn,
            codes,
            presented_token,
            allow_reissue=credential_reissue_authorized,
            rotate=rotate_credential,
        )
        if token:
            result["deviceToken"] = token
            result["credentialVersion"] = DEVICE_CREDENTIAL_VERSION
        else:
            result["deviceCredentialRequired"] = True
    return result


PAYMENT_TYPES = {
    "alipay": "支付宝",
    "wxpay": "微信支付",
}


def payment_amount_cents(value=None):
    raw = PAYMENT_ACTIVATION_PRICE if value is None else value
    try:
        amount = Decimal(str(raw)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    except (InvalidOperation, TypeError, ValueError) as exc:
        raise PaymentConfigurationError("payment activation price is invalid") from exc
    cents = int(amount * 100)
    if cents <= 0:
        raise PaymentConfigurationError("payment activation price must be positive")
    return cents


def payment_amount_text(cents):
    return f"{int(cents) / 100:.2f}"


def payment_order_number():
    stamp = datetime.utcnow().strftime("%Y%m%d%H%M%S")
    return f"WJX{stamp}{secrets.token_hex(5).upper()}"


def payment_expiry_iso():
    return (
        utc_datetime() + timedelta(seconds=PAYMENT_ORDER_TTL_SECONDS)
    ).replace(microsecond=0).isoformat() + "Z"


def payment_order_expired(order, now=None):
    """Use both persisted expiry and the current provider TTL.

    The creation-time check keeps orders created before a TTL configuration
    change from reusing a payment page that is already stale at the provider.
    """
    current = now or utc_datetime()
    stored_expiry = parse_utc(order["expires_at"])
    created_at = parse_utc(order["created_at"])
    if stored_expiry and stored_expiry <= current:
        return True
    return bool(
        created_at
        and created_at + timedelta(seconds=PAYMENT_ORDER_TTL_SECONDS) <= current
    )


def payment_poll_token_matches(order, token):
    expected = str(order["poll_token_hash"] or "")
    candidate = device_token_hash(token) if token else ""
    return bool(expected and candidate and secrets.compare_digest(expected, candidate))


def payment_device_authorized(conn, order, presented_token):
    try:
        aliases = json.loads(order["device_codes_json"] or "[]")
    except (TypeError, ValueError, json.JSONDecodeError):
        aliases = []
    codes = list(dict.fromkeys([order["device_code"], *aliases]))
    rows = conn.execute(
        f"SELECT * FROM devices WHERE device_code IN ({sql_placeholders(codes)})",
        codes,
    ).fetchall()
    return bool(presented_token and any(device_token_matches(row, presented_token) for row in rows))


def payment_checkout_mode(payment_type, pay_info):
    value = str(pay_info or "").strip()
    if not value or len(value) > 4096 or any(ord(char) < 32 or ord(char) == 127 for char in value):
        return ""
    parsed = urlsplit(value)
    scheme = parsed.scheme.lower()
    if scheme == "https" and parsed.hostname and not parsed.username and not parsed.password:
        return "hosted"
    if not scheme:
        return "qrcode"
    allowed_schemes = {
        "alipay": {"alipay", "alipays"},
        "wxpay": {"weixin"},
    }
    if scheme in allowed_schemes.get(str(payment_type or "").strip().lower(), set()):
        return "qrcode"
    return ""


def payment_order_client_payload(order, poll_token=""):
    result = {
        "success": True,
        "orderNo": order["out_trade_no"],
        "paymentType": order["payment_type"],
        "paymentName": PAYMENT_TYPES.get(order["payment_type"], order["payment_type"]),
        "payType": order["pay_payload_type"],
        "checkoutMode": payment_checkout_mode(order["payment_type"], order["pay_info"]) or "unsupported",
        "payInfo": order["pay_info"],
        # payInfo is the provider's hosted checkout page in V2 mode. Keep a
        # named alias so clients do not mistake it for a QR payload and encode
        # it into a second QR code.
        "payUrl": order["pay_info"],
        "amount": payment_amount_text(order["expected_amount_cents"]),
        "status": order["status"],
        "paid": order["status"] == "paid",
        "expiresAt": epoch_millis(order["expires_at"]),
    }
    if poll_token:
        result["pollToken"] = poll_token
    return result


def provider_result_matches_order(result, order, require_paid=False):
    if str(result.get("pid") or "") != PAYMENT_PID:
        return False, "支付平台商户号不匹配"
    if str(result.get("out_trade_no") or "") != order["out_trade_no"]:
        return False, "支付平台订单号不匹配"
    if require_paid and "money" not in result:
        return False, "支付平台回包缺少金额"
    if "money" in result:
        try:
            paid_cents = payment_amount_cents(result.get("money"))
        except PaymentConfigurationError:
            return False, "支付平台金额格式错误"
        if paid_cents != int(order["expected_amount_cents"]):
            return False, "支付金额不匹配"
    if require_paid and str(result.get("status") or "0") != "1":
        return False, "订单尚未支付"
    return True, ""


def ensure_payment_license(conn, order, provider_result, payload_hash=""):
    current = conn.execute(
        "SELECT * FROM payment_orders WHERE id = ?",
        (order["id"],),
    ).fetchone()
    if current is None:
        raise RuntimeError("payment order disappeared")
    if current["status"] == "paid" and current["license_code_id"]:
        return current

    device_codes = [current["device_code"]]
    license_row = license_for_device_codes(conn, device_codes)
    now = utc_now()
    if license_row is None:
        for _attempt in range(20):
            code = generate_license_code()
            try:
                cursor = conn.execute(
                    """
                    INSERT INTO license_codes
                        (code, device_code, raw_device_code, note, enabled, activated_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                    """,
                    (
                        code,
                        current["device_code"],
                        current["raw_device_code"],
                        f"扫码支付 {current['out_trade_no']}",
                        now,
                        now,
                        now,
                    ),
                )
                license_row = conn.execute(
                    "SELECT * FROM license_codes WHERE id = ?",
                    (cursor.lastrowid,),
                ).fetchone()
                break
            except sqlite3.IntegrityError:
                continue
    if license_row is None:
        raise RuntimeError("unable to create payment license")

    upsert_device_group(
        conn,
        device_codes,
        current["raw_device_code"],
        current["app_version"],
        current["package_name"],
        True,
    )
    conn.execute(
        """
        UPDATE payment_orders
        SET provider_trade_no = ?, paid_amount_cents = ?, status = 'paid',
            license_code_id = ?, notify_payload_hash = ?, paid_at = COALESCE(paid_at, ?),
            last_provider_query_at = ?, updated_at = ?
        WHERE id = ?
        """,
        (
            str(provider_result.get("trade_no") or current["provider_trade_no"]),
            int(current["expected_amount_cents"]),
            license_row["id"],
            payload_hash or current["notify_payload_hash"],
            now,
            now,
            now,
            current["id"],
        ),
    )
    log_event(
        conn,
        "payment",
        f"订单 {current['out_trade_no']} 支付成功，车辆 {current['device_code']} 已自动激活",
        request.remote_addr,
    )
    return conn.execute(
        "SELECT * FROM payment_orders WHERE id = ?",
        (current["id"],),
    ).fetchone()


def refresh_payment_order_from_provider(out_trade_no, payload_hash=""):
    with db() as conn:
        order = conn.execute(
            "SELECT * FROM payment_orders WHERE out_trade_no = ?",
            (out_trade_no,),
        ).fetchone()
        if order is None:
            return None
        if order["status"] == "paid":
            return order
    result = query_provider_order(out_trade_no)
    matched, message = provider_result_matches_order(result, order)
    if not matched:
        raise PaymentProviderError(message)
    with db() as conn:
        current = conn.execute(
            "SELECT * FROM payment_orders WHERE out_trade_no = ?",
            (out_trade_no,),
        ).fetchone()
        if current is None:
            return None
        if str(result.get("status") or "0") == "1":
            matched, message = provider_result_matches_order(result, current, require_paid=True)
            if not matched:
                raise PaymentProviderError(message)
            return ensure_payment_license(conn, current, result, payload_hash)
        now = utc_now()
        conn.execute(
            "UPDATE payment_orders SET last_provider_query_at = ?, updated_at = ? WHERE id = ?",
            (now, now, current["id"]),
        )
        return conn.execute(
            "SELECT * FROM payment_orders WHERE id = ?",
            (current["id"],),
        ).fetchone()


def paid_payment_payload(conn, order, presented_token):
    device_codes = [order["device_code"]]
    license_row = conn.execute(
        "SELECT * FROM license_codes WHERE id = ? AND enabled = 1",
        (order["license_code_id"],),
    ).fetchone()
    if license_row is None:
        return {
            "success": False,
            "paid": True,
            "status": "paid",
            "activated": False,
            "message": "支付已到账，但授权生成异常，请联系客服",
        }
    result = device_license_payload(
        conn,
        order["device_code"],
        order["raw_device_code"],
        order["app_version"],
        order["package_name"],
        True,
        device_codes=device_codes,
        credential_requested=True,
        presented_token=presented_token,
        credential_reissue_authorized=True,
    )
    activated_at_iso, activated_at_ms = license_row_activated_at(conn, license_row)
    result.update({
        "paid": True,
        "status": "paid",
        "orderNo": order["out_trade_no"],
        "activatedAt": activated_at_ms,
        "activatedAtIso": activated_at_iso,
        "activationCodeMasked": mask_license_code(license_row["code"]),
        "message": "支付成功，永久授权已自动激活",
    })
    offline_license = build_offline_license(
        device_codes,
        order["package_name"],
        license_row["code"],
        activated_at_ms,
    )
    if offline_license is not None:
        result["offlineLicense"] = offline_license
    return result


@app.post("/api/v1/license/status")
def api_license_status():
    payload = request.get_json(silent=True) or {}
    device_codes = collect_device_codes(payload)
    device_code = device_codes[0] if device_codes else ""
    raw_device = payload.get("rawDeviceCode", "")
    app_version = payload.get("appVersion", "")
    package_name = payload.get("packageName", "")
    credential_requested = requested_device_credential(payload)
    offline_proof_authorized = verify_offline_license_proof(
        payload.get("deviceProof"),
        [device_code],
        package_name,
    ) if credential_requested else False
    if not device_code:
        return jsonify({"success": False, "activated": False, "message": "缺少车架号"}), 400
    with db() as conn:
        row = license_for_device_codes(conn, [device_code])
        activated = row is not None
        response_device_code = device_code
        presented_token = request_device_token()
        existing_device = conn.execute(
            "SELECT * FROM devices WHERE device_code = ?",
            (device_code,),
        ).fetchone()
        credential_authorized = offline_proof_authorized or device_token_matches(
            existing_device,
            presented_token,
        )
        result = device_license_payload(
            conn,
            response_device_code,
            raw_device,
            app_version,
            package_name,
            activated,
            device_codes=[device_code],
            credential_requested=credential_requested,
            presented_token=presented_token,
            credential_reissue_authorized=offline_proof_authorized,
        )
        if row is not None and credential_authorized:
            activated_at_iso, activated_at_ms = license_row_activated_at(conn, row)
            result["activatedAt"] = activated_at_ms
            result["activatedAtIso"] = activated_at_iso
            offline_license = build_offline_license(
                [response_device_code],
                package_name,
                row["code"],
                activated_at_ms,
            )
            if offline_license is not None:
                result["offlineLicense"] = offline_license
    return jsonify(result)


@app.post("/api/v1/license/activate")
def api_license_activate():
    payload = request.get_json(silent=True) or {}
    device_codes = collect_device_codes(payload)
    device_code = device_codes[0] if device_codes else ""
    raw_device = payload.get("rawDeviceCode", "")
    app_version = payload.get("appVersion", "")
    package_name = payload.get("packageName", "")
    credential_requested = requested_device_credential(payload)
    code = format_license_code(payload.get("activationCode", ""))
    if not device_code:
        return jsonify({"success": False, "activated": False, "message": "缺少车架号"}), 400
    if not code:
        return jsonify({"success": False, "activated": False, "message": "缺少激活码"}), 400
    with db() as conn:
        attempt_key = api_attempt_key("license_activate", device_code)
        remaining = api_lock_remaining(conn, attempt_key)
        if remaining > 0:
            return jsonify({
                "success": False,
                "activated": False,
                "message": f"请求过于频繁，请 {remaining} 秒后再试",
            }), 429
        if not is_valid_license_code(code):
            upsert_device_group(conn, [device_code], raw_device, app_version, package_name, False)
            remaining = record_api_failure(conn, attempt_key)
            log_event(conn, "license", f"激活失败：激活码格式不正确 {code}", request.remote_addr)
            if remaining > 0:
                return jsonify({
                    "success": False,
                    "activated": False,
                    "message": f"请求过于频繁，请 {remaining} 秒后再试",
                }), 429
            return jsonify({"success": False, "activated": False, "message": "激活码格式不正确"}), 400
        row = conn.execute("SELECT * FROM license_codes WHERE code = ?", (code,)).fetchone()
        if row is None or not row["enabled"]:
            upsert_device_group(conn, [device_code], raw_device, app_version, package_name, False)
            remaining = record_api_failure(conn, attempt_key)
            log_event(conn, "license", f"激活失败：无效激活码 {code}", request.remote_addr)
            if remaining > 0:
                return jsonify({
                    "success": False,
                    "activated": False,
                    "message": f"请求过于频繁，请 {remaining} 秒后再试",
                }), 429
            return jsonify({"success": False, "activated": False, "message": "激活码无效或已停用"}), 403
        bound = row["device_code"]
        if bound and bound != device_code:
            upsert_device_group(conn, [device_code], raw_device, app_version, package_name, False)
            remaining = record_api_failure(conn, attempt_key)
            log_event(conn, "license", f"激活失败：{code} 已绑定其他车辆", request.remote_addr)
            if remaining > 0:
                return jsonify({
                    "success": False,
                    "activated": False,
                    "message": f"请求过于频繁，请 {remaining} 秒后再试",
                }), 429
            return jsonify({"success": False, "activated": False, "message": "激活码已绑定其他车辆"}), 409
        now = utc_now()
        bound_device_code = bound or device_code
        conn.execute(
            """
            UPDATE license_codes
            SET device_code = ?, raw_device_code = ?, activated_at = COALESCE(activated_at, ?), updated_at = ?
            WHERE id = ?
            """,
            (bound_device_code, raw_device, now, now, row["id"]),
        )
        upsert_device_group(conn, [device_code], raw_device, app_version, package_name, True)
        record_api_success(conn, attempt_key)
        log_event(conn, "license", f"车辆 {bound_device_code} 激活成功", request.remote_addr)
        result = device_license_payload(
            conn,
            bound_device_code,
            raw_device,
            app_version,
            package_name,
            True,
            device_codes=[device_code],
            credential_requested=credential_requested,
            presented_token=request_device_token(),
            rotate_credential=credential_requested,
        )
        result["activatedAt"] = epoch_millis(now)
        result["activatedAtIso"] = now
        result["message"] = "激活成功，已获得永久授权"
        offline_license = build_offline_license(
            [bound_device_code],
            package_name,
            code,
            result["activatedAt"],
        )
        if offline_license is not None:
            result["offlineLicense"] = offline_license
    return jsonify(result)


@app.post("/api/v1/license/payment/create")
def api_payment_create():
    payload = request.get_json(silent=True) or {}
    payment_type = str(payload.get("paymentType") or "").strip().lower()
    device_codes = collect_device_codes(payload)
    device_code = device_codes[0] if device_codes else ""
    raw_device = str(payload.get("rawDeviceCode") or "")
    app_version = str(payload.get("appVersion") or "")[:64]
    package_name = str(payload.get("packageName") or "")[:160]
    if payment_type not in PAYMENT_TYPES:
        return jsonify({"success": False, "message": "请选择支付宝或微信支付"}), 400
    if not device_code:
        return jsonify({"success": False, "message": "未读取到车架号，暂时无法创建订单"}), 400
    try:
        expected_cents = payment_amount_cents()
        if not payment_ready():
            raise PaymentConfigurationError("payment RSA keys are not configured")
    except PaymentConfigurationError:
        return jsonify({"success": False, "message": "支付服务尚未完成配置"}), 503

    poll_token = secrets.token_urlsafe(32)
    with db() as conn:
        if license_for_device_codes(conn, [device_code]) is not None:
            return jsonify({"success": False, "message": "当前车辆已经永久激活，无需重复购买"}), 409
        upsert_device_group(conn, [device_code], raw_device, app_version, package_name, False)
        reusable = conn.execute(
            """
            SELECT * FROM payment_orders
            WHERE device_code = ?
              AND payment_type = ? AND status = 'pending' AND expires_at > ?
              AND created_at > ?
              AND pay_info != ''
            ORDER BY id DESC LIMIT 1
            """,
            (
                device_code,
                payment_type,
                utc_now(),
                (utc_datetime() - timedelta(seconds=PAYMENT_ORDER_TTL_SECONDS))
                .replace(microsecond=0)
                .isoformat()
                + "Z",
            ),
        ).fetchone()
        if reusable is not None:
            conn.execute(
                "UPDATE payment_orders SET poll_token_hash = ?, updated_at = ? WHERE id = ?",
                (device_token_hash(poll_token), utc_now(), reusable["id"]),
            )
            refreshed = conn.execute(
                "SELECT * FROM payment_orders WHERE id = ?",
                (reusable["id"],),
            ).fetchone()
            return jsonify(payment_order_client_payload(refreshed, poll_token))

        out_trade_no = payment_order_number()
        now = utc_now()
        expires_at = payment_expiry_iso()
        title = f"{PAYMENT_SITE_NAME}永久授权"
        conn.execute(
            """
            INSERT INTO payment_orders
                (out_trade_no, device_code, raw_device_code, device_codes_json,
                 app_version, package_name, payment_type, title, expected_amount_cents,
                 poll_token_hash, created_at, expires_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                out_trade_no,
                device_code,
                raw_device,
                json.dumps([device_code], ensure_ascii=True, separators=(",", ":")),
                app_version,
                package_name,
                payment_type,
                title,
                expected_cents,
                device_token_hash(poll_token),
                now,
                expires_at,
                now,
            ),
        )

    try:
        provider = create_provider_order({
            "type": payment_type,
            "method": "scan",
            "device": "pc",
            "out_trade_no": out_trade_no,
            "notify_url": f"{PUBLIC_API_BASE_URL}/api/v1/license/payment/notify",
            "return_url": f"{PUBLIC_API_BASE_URL}/api/v1/license/payment/return?out_trade_no={out_trade_no}",
            "name": title,
            "money": payment_amount_text(expected_cents),
        })
        pay_type = str(provider.get("pay_type") or "").strip().lower()
        # V2 providers may return the hosted checkout page as `payurl`/`url`
        # while older responses call the same value `pay_info`.
        pay_info = next(
            (
                str(provider.get(key) or "").strip()
                for key in ("payurl", "pay_url", "url", "pay_info", "qrcode")
                if str(provider.get(key) or "").strip()
            ),
            "",
        )
        checkout_mode = payment_checkout_mode(payment_type, pay_info)
        valid_pay_type = pay_type in {"qrcode", "jump", "urlscheme"}
        valid_mode_pair = (
            (pay_type == "qrcode" and checkout_mode in {"hosted", "qrcode"})
            or (pay_type == "jump" and checkout_mode == "hosted")
            or (pay_type == "urlscheme" and checkout_mode == "qrcode")
        )
        if not valid_pay_type or not checkout_mode or not valid_mode_pair:
            raise PaymentProviderError("支付平台没有返回可用的付款信息")
        with db() as conn:
            now = utc_now()
            conn.execute(
                """
                UPDATE payment_orders
                SET provider_trade_no = ?, pay_payload_type = ?, pay_info = ?, updated_at = ?
                WHERE out_trade_no = ?
                """,
                (
                    str(provider.get("trade_no") or ""),
                    pay_type,
                    pay_info,
                    now,
                    out_trade_no,
                ),
            )
            order = conn.execute(
                "SELECT * FROM payment_orders WHERE out_trade_no = ?",
                (out_trade_no,),
            ).fetchone()
            log_event(conn, "payment", f"车辆 {device_code} 创建{PAYMENT_TYPES[payment_type]}订单 {out_trade_no}")
        return jsonify(payment_order_client_payload(order, poll_token))
    except (PaymentConfigurationError, PaymentProviderError) as exc:
        with db() as conn:
            conn.execute(
                "UPDATE payment_orders SET status = 'failed', updated_at = ? WHERE out_trade_no = ?",
                (utc_now(), out_trade_no),
            )
            log_event(conn, "payment", f"订单 {out_trade_no} 创建失败：{str(exc)[:180]}")
        return jsonify({"success": False, "message": f"创建支付订单失败：{exc}"}), 502


@app.post("/api/v1/license/payment/status")
def api_payment_status():
    payload = request.get_json(silent=True) or {}
    out_trade_no = str(payload.get("orderNo") or "").strip()[:80]
    poll_token = str(payload.get("pollToken") or "").strip()[:256]
    presented_token = request_device_token()
    if not out_trade_no or not poll_token:
        return jsonify({"success": False, "message": "缺少支付订单凭据"}), 400
    with db() as conn:
        order = conn.execute(
            "SELECT * FROM payment_orders WHERE out_trade_no = ?",
            (out_trade_no,),
        ).fetchone()
        if order is None:
            return jsonify({"success": False, "message": "支付订单不存在"}), 404
        if not payment_poll_token_matches(order, poll_token):
            return jsonify({"success": False, "message": "支付订单凭据无效"}), 403
        if order["status"] == "pending" and payment_order_expired(order):
            conn.execute(
                "UPDATE payment_orders SET status = 'expired', updated_at = ? WHERE id = ?",
                (utc_now(), order["id"]),
            )
            order = conn.execute(
                "SELECT * FROM payment_orders WHERE id = ?",
                (order["id"],),
            ).fetchone()
        last_query = parse_utc(order["last_provider_query_at"])
        query_due = (
            order["status"] == "pending"
            and (last_query is None or last_query <= utc_datetime() - timedelta(seconds=PAYMENT_QUERY_INTERVAL_SECONDS))
        )
    if query_due:
        try:
            order = refresh_payment_order_from_provider(out_trade_no) or order
        except (PaymentConfigurationError, PaymentProviderError):
            pass
    with db() as conn:
        order = conn.execute(
            "SELECT * FROM payment_orders WHERE out_trade_no = ?",
            (out_trade_no,),
        ).fetchone()
        if order["status"] == "paid":
            return jsonify(paid_payment_payload(conn, order, presented_token))
        result = payment_order_client_payload(order)
        result["message"] = {
            "pending": "等待扫码支付",
            "expired": "支付二维码已过期，请重新创建订单",
            "failed": "支付订单创建失败，请重新尝试",
        }.get(order["status"], "等待支付结果")
        return jsonify(result)


@app.route("/api/v1/license/payment/notify", methods=["GET", "POST"])
def api_payment_notify():
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        source = request.form if request.method == "POST" else request.args
        payload = source.to_dict(flat=True)
    try:
        if not verify_payment_params(payload):
            return "fail", 400, {"Content-Type": "text/plain; charset=utf-8"}
    except PaymentConfigurationError:
        return "fail", 503, {"Content-Type": "text/plain; charset=utf-8"}
    out_trade_no = str(payload.get("out_trade_no") or "").strip()[:80]
    if not out_trade_no or str(payload.get("pid") or "") != PAYMENT_PID:
        return "fail", 400, {"Content-Type": "text/plain; charset=utf-8"}
    with db() as conn:
        order = conn.execute(
            "SELECT * FROM payment_orders WHERE out_trade_no = ?",
            (out_trade_no,),
        ).fetchone()
    if order is None:
        return "fail", 404, {"Content-Type": "text/plain; charset=utf-8"}
    matched, _message = provider_result_matches_order(payload, order)
    if not matched:
        return "fail", 400, {"Content-Type": "text/plain; charset=utf-8"}
    payload_hash = hashlib.sha256(
        json.dumps(payload, sort_keys=True, ensure_ascii=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    try:
        refreshed = refresh_payment_order_from_provider(out_trade_no, payload_hash)
    except (PaymentConfigurationError, PaymentProviderError):
        return "fail", 502, {"Content-Type": "text/plain; charset=utf-8"}
    if refreshed is None or refreshed["status"] != "paid":
        return "fail", 409, {"Content-Type": "text/plain; charset=utf-8"}
    return "success", 200, {"Content-Type": "text/plain; charset=utf-8"}


@app.get("/api/v1/license/payment/return")
def api_payment_return():
    return """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>支付结果</title><style>body{margin:0;background:#f5f7fb;color:#18202a;font-family:system-ui,-apple-system,sans-serif;display:grid;place-items:center;min-height:100vh}.box{max-width:420px;margin:24px;padding:28px;background:#fff;border:1px solid #e6eaf0;border-radius:16px;text-align:center;box-shadow:0 16px 50px rgba(25,35,45,.08)}h1{font-size:22px;margin:0 0 10px}p{color:#607080;line-height:1.7;margin:0}</style></head>
<body><div class="box"><h1>支付结果已提交</h1><p>请返回车机上的超级玩具箱，系统核实到账后会自动完成永久激活。</p></div></body></html>"""


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=8095)
