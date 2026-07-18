import os
import sqlite3
import threading
from datetime import datetime

from werkzeug.security import check_password_hash, generate_password_hash

from platform_config import (
    APK_DIR,
    DATA_DIR,
    DB_PATH,
    DEFAULT_CONFIG,
    DEFAULT_TRIAL_DAYS,
    LEGACY_DEFAULT_ADMIN_PASSWORD,
    MIN_ADMIN_PASSWORD_LENGTH,
    PASSWORD_HASH_METHOD,
    SERVICE_QR_DIR,
    SERVICE_QR_TYPES,
    SOUND_DIR,
)


_db_init_lock = threading.Lock()
_db_initialized = False


def utc_now():
    return datetime.utcnow().replace(microsecond=0).isoformat() + "Z"


def db():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def ensure_column(conn, table, column, definition):
    columns = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})").fetchall()}
    if column not in columns:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")


def ensure_config_defaults(conn):
    for key, value in DEFAULT_CONFIG.items():
        conn.execute("INSERT OR IGNORE INTO config (key, value) VALUES (?, ?)", (key, value))
    conn.execute(
        "UPDATE config SET value = ? WHERE key = 'license_trial_days' AND value = '3'",
        (str(DEFAULT_TRIAL_DAYS),),
    )


def configured_admin_credentials():
    username = os.environ.get("WJX_PLATFORM_ADMIN_USER", "admin").strip() or "admin"
    password = os.environ.get("WJX_PLATFORM_ADMIN_PASSWORD", "").strip()
    if not password:
        raise RuntimeError(
            "WJX_PLATFORM_ADMIN_PASSWORD must be configured before the first platform startup"
        )
    if password == LEGACY_DEFAULT_ADMIN_PASSWORD:
        raise RuntimeError("WJX_PLATFORM_ADMIN_PASSWORD must not use the legacy default password")
    if len(password) < MIN_ADMIN_PASSWORD_LENGTH:
        raise RuntimeError(
            f"WJX_PLATFORM_ADMIN_PASSWORD must contain at least {MIN_ADMIN_PASSWORD_LENGTH} characters"
        )
    return username, password


def init_db(force=False):
    global _db_initialized
    if _db_initialized and not force:
        return
    with _db_init_lock:
        if _db_initialized and not force:
            return
        DATA_DIR.mkdir(parents=True, exist_ok=True)
        APK_DIR.mkdir(parents=True, exist_ok=True)
        SOUND_DIR.mkdir(parents=True, exist_ok=True)
        SERVICE_QR_DIR.mkdir(parents=True, exist_ok=True)
        with db() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS admins (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    username TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS releases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    version TEXT NOT NULL,
                    version_code INTEGER NOT NULL DEFAULT 0,
                    apk_filename TEXT NOT NULL,
                    apk_size INTEGER NOT NULL DEFAULT 0,
                    release_notes TEXT NOT NULL DEFAULT '',
                    force_update INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS sounds (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT '',
                    tags TEXT NOT NULL DEFAULT '',
                    description TEXT NOT NULL DEFAULT '',
                    filename TEXT NOT NULL,
                    original_filename TEXT NOT NULL DEFAULT '',
                    mime_type TEXT NOT NULL DEFAULT '',
                    file_size INTEGER NOT NULL DEFAULT 0,
                    duration_ms INTEGER NOT NULL DEFAULT 0,
                    download_count INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS license_codes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    device_code TEXT NOT NULL DEFAULT '',
                    raw_device_code TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    activated_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS devices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_code TEXT NOT NULL UNIQUE,
                    raw_device_code TEXT NOT NULL DEFAULT '',
                    app_version TEXT NOT NULL DEFAULT '',
                    package_name TEXT NOT NULL DEFAULT '',
                    activated INTEGER NOT NULL DEFAULT 0,
                    trial_started_at TEXT,
                    trial_ends_at TEXT,
                    first_seen_at TEXT NOT NULL,
                    last_seen_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    message TEXT NOT NULL,
                    remote_addr TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS config (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS service_qrcodes (
                    key TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    subtitle TEXT NOT NULL DEFAULT '',
                    description TEXT NOT NULL DEFAULT '',
                    filename TEXT NOT NULL DEFAULT '',
                    original_filename TEXT NOT NULL DEFAULT '',
                    mime_type TEXT NOT NULL DEFAULT '',
                    file_size INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    expires_at TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS login_attempts (
                    client_key TEXT PRIMARY KEY,
                    fail_count INTEGER NOT NULL DEFAULT 0,
                    locked_until INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS api_attempts (
                    client_key TEXT PRIMARY KEY,
                    fail_count INTEGER NOT NULL DEFAULT 0,
                    locked_until INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT NOT NULL
                );
                """
            )
            ensure_column(conn, "devices", "trial_started_at", "TEXT")
            ensure_column(conn, "devices", "trial_ends_at", "TEXT")
            ensure_config_defaults(conn)
            now = utc_now()
            for key, (title, subtitle, description) in SERVICE_QR_TYPES.items():
                conn.execute(
                    """
                    INSERT OR IGNORE INTO service_qrcodes
                        (key, title, subtitle, description, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 1, ?, ?)
                    """,
                    (key, title, subtitle, description, now, now),
                )
                conn.execute(
                    """
                    UPDATE service_qrcodes
                    SET title = ?, subtitle = ?
                    WHERE key = ?
                    """,
                    (title, subtitle, key),
                )
            admin = conn.execute(
                "SELECT id, username, password_hash FROM admins WHERE id = 1"
            ).fetchone()
            legacy_default = bool(
                admin
                and check_password_hash(
                    admin["password_hash"],
                    LEGACY_DEFAULT_ADMIN_PASSWORD,
                )
            )
            if admin is None or legacy_default:
                username, password = configured_admin_credentials()
                conn.execute(
                    """
                    INSERT INTO admins (id, username, password_hash, updated_at)
                    VALUES (1, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        username = excluded.username,
                        password_hash = excluded.password_hash,
                        updated_at = excluded.updated_at
                    """,
                    (username, generate_password_hash(password, method=PASSWORD_HASH_METHOD), utc_now()),
                )
        _db_initialized = True
