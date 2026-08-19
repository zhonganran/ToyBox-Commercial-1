import fcntl
import os
import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime, timezone

from werkzeug.security import generate_password_hash

from platform_config import (
    APK_DIR,
    DATA_DIR,
    DB_PATH,
    DEFAULT_CONFIG,
    INSECURE_ADMIN_PASSWORD,
    LOG_DIR,
    MIN_ADMIN_PASSWORD_LENGTH,
    PASSWORD_HASH_METHOD,
    SERVICE_QR_DIR,
    SERVICE_QR_TYPES,
    SOUND_DIR,
)


_db_init_lock = threading.Lock()
_db_initialized = False
DB_BUSY_TIMEOUT_MS = 10_000
PLATFORM_SCHEMA_VERSION = 1


def utc_datetime():
    """Return a naive UTC datetime for compatibility with stored ISO timestamps."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def utc_now():
    return utc_datetime().replace(microsecond=0).isoformat() + "Z"


@contextmanager
def db():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=DB_BUSY_TIMEOUT_MS / 1000)
    conn.row_factory = sqlite3.Row
    conn.execute(f"PRAGMA busy_timeout = {DB_BUSY_TIMEOUT_MS}")
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


@contextmanager
def database_init_file_lock():
    """Serialize database initialization across Gunicorn worker processes."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    lock_path = DB_PATH.parent / f"{DB_PATH.name}.init.lock"
    with lock_path.open("a+b") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def ensure_config_defaults(conn):
    for key, value in DEFAULT_CONFIG.items():
        conn.execute("INSERT OR IGNORE INTO config (key, value) VALUES (?, ?)", (key, value))


def configured_admin_credentials():
    username = os.environ.get("WJX_PLATFORM_ADMIN_USER", "admin").strip() or "admin"
    password = os.environ.get("WJX_PLATFORM_ADMIN_PASSWORD", "").strip()
    if not password:
        raise RuntimeError(
            "WJX_PLATFORM_ADMIN_PASSWORD must be configured before the first platform startup"
        )
    if password == INSECURE_ADMIN_PASSWORD:
        raise RuntimeError("WJX_PLATFORM_ADMIN_PASSWORD must not use a known insecure password")
    if len(password) < MIN_ADMIN_PASSWORD_LENGTH:
        raise RuntimeError(
            f"WJX_PLATFORM_ADMIN_PASSWORD must contain at least {MIN_ADMIN_PASSWORD_LENGTH} characters"
        )
    return username, password


def validate_existing_schema(conn):
    tables = {
        row["name"]
        for row in conn.execute(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
            """
        ).fetchall()
    }
    if not tables:
        return False
    if "platform_schema" not in tables:
        raise RuntimeError(
            "Existing platform database has no current schema marker; "
            "start with an empty database instead of migrating an older database"
        )
    try:
        marker = conn.execute(
            "SELECT schema_version FROM platform_schema WHERE id = 1"
        ).fetchone()
    except sqlite3.DatabaseError as exc:
        raise RuntimeError(
            "Existing platform database has an invalid schema marker; "
            "start with an empty database"
        ) from exc
    if marker is None or marker["schema_version"] != PLATFORM_SCHEMA_VERSION:
        found_version = "missing" if marker is None else marker["schema_version"]
        raise RuntimeError(
            f"Unsupported platform database schema version {found_version}; "
            f"expected {PLATFORM_SCHEMA_VERSION}. Start with an empty database"
        )
    return True


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
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        with database_init_file_lock(), db() as conn:
            conn.execute("PRAGMA journal_mode = WAL")
            conn.execute("PRAGMA synchronous = NORMAL")
            existing_schema = validate_existing_schema(conn)
            conn.executescript(
                """
                BEGIN IMMEDIATE;
                CREATE TABLE IF NOT EXISTS platform_schema (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    schema_version INTEGER NOT NULL
                );
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
                    last_seen_at TEXT NOT NULL,
                    api_token_hash TEXT NOT NULL DEFAULT '',
                    api_token_issued_at TEXT
                );
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    message TEXT NOT NULL,
                    remote_addr TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS diagnostic_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_code TEXT NOT NULL,
                    app_version TEXT NOT NULL DEFAULT '',
                    package_name TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    filename TEXT NOT NULL UNIQUE,
                    original_filename TEXT NOT NULL DEFAULT '',
                    mime_type TEXT NOT NULL DEFAULT '',
                    file_size INTEGER NOT NULL DEFAULT 0,
                    remote_addr TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_diagnostic_logs_device_created
                    ON diagnostic_logs(device_code, created_at);
                CREATE INDEX IF NOT EXISTS idx_diagnostic_logs_ip_created
                    ON diagnostic_logs(remote_addr, created_at);
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
            if not existing_schema:
                conn.execute(
                    "INSERT INTO platform_schema (id, schema_version) VALUES (1, ?)",
                    (PLATFORM_SCHEMA_VERSION,),
                )
            now = utc_now()
            ensure_config_defaults(conn)
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
                "SELECT id FROM admins WHERE id = 1"
            ).fetchone()
            if admin is None:
                username, password = configured_admin_credentials()
                conn.execute(
                    """
                    INSERT INTO admins (id, username, password_hash, updated_at)
                    VALUES (1, ?, ?, ?)
                    """,
                    (username, generate_password_hash(password, method=PASSWORD_HASH_METHOD), utc_now()),
                )
        _db_initialized = True
