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
    LOG_DIR,
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


DEVICE_SCHEMA_COLUMNS = (
    "id",
    "device_code",
    "raw_device_code",
    "app_version",
    "package_name",
    "activated",
    "trial_started_at",
    "trial_ends_at",
    "first_seen_at",
    "last_seen_at",
    "vehicle_name",
    "vin_year",
    "model_variant",
    "car_series",
    "vehicle_type",
    "controller_version",
    "controller_series",
    "power_state",
    "runtime_state",
    "remote_addr",
    "api_token_hash",
    "api_token_issued_at",
    "soc",
    "electric_range_km",
    "fuel_range_km",
    "gear",
    "lock_state",
    "inside_temp",
    "outside_temp",
    "mileage_km",
    "voltage_12v",
    "charging_status",
    "telemetry_updated_at",
)
LEGACY_DEVICE_CONFIGURATION_COLUMNS = {
    "sales_model",
    "vehicle_40d_code",
    "controller_compat",
    "compatibility_key",
    "camera_source_fingerprint",
    "current_profile_id",
    "current_profile_version",
    "manual_override",
    "camera_id",
    "surface_index",
    "left_source",
    "right_source",
    "rear_source",
    "profile_status",
    "profile_applied_at",
}


def remove_legacy_vehicle_configuration(conn):
    columns = {row["name"] for row in conn.execute("PRAGMA table_info(devices)").fetchall()}
    if columns & LEGACY_DEVICE_CONFIGURATION_COLUMNS:
        selected = ", ".join(DEVICE_SCHEMA_COLUMNS)
        conn.executescript(
            """
            DROP TABLE IF EXISTS devices_without_profiles;
            CREATE TABLE devices_without_profiles (
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
                vehicle_name TEXT NOT NULL DEFAULT '',
                vin_year INTEGER,
                model_variant TEXT NOT NULL DEFAULT '',
                car_series TEXT NOT NULL DEFAULT '',
                vehicle_type TEXT NOT NULL DEFAULT '',
                controller_version TEXT NOT NULL DEFAULT '',
                controller_series INTEGER NOT NULL DEFAULT 0,
                power_state INTEGER,
                runtime_state TEXT NOT NULL DEFAULT '',
                remote_addr TEXT NOT NULL DEFAULT '',
                api_token_hash TEXT NOT NULL DEFAULT '',
                api_token_issued_at TEXT,
                soc INTEGER,
                electric_range_km INTEGER,
                fuel_range_km INTEGER,
                gear INTEGER,
                lock_state INTEGER,
                inside_temp INTEGER,
                outside_temp INTEGER,
                mileage_km REAL,
                voltage_12v REAL,
                charging_status INTEGER,
                telemetry_updated_at TEXT
            );
            """
        )
        conn.execute(
            f"INSERT INTO devices_without_profiles ({selected}) SELECT {selected} FROM devices"
        )
        conn.executescript(
            """
            DROP TABLE devices;
            ALTER TABLE devices_without_profiles RENAME TO devices;
            """
        )
    conn.execute("DROP TABLE IF EXISTS vehicle_profiles")
    conn.execute("DROP TABLE IF EXISTS vehicle_models")


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
        LOG_DIR.mkdir(parents=True, exist_ok=True)
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
                CREATE TABLE IF NOT EXISTS miniapp_users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    openid TEXT NOT NULL UNIQUE,
                    unionid TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    last_login_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS miniapp_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    token_hash TEXT NOT NULL UNIQUE,
                    expires_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    last_seen_at TEXT NOT NULL,
                    remote_addr TEXT NOT NULL DEFAULT ''
                );
                CREATE INDEX IF NOT EXISTS idx_miniapp_sessions_user_expiry
                    ON miniapp_sessions(user_id, expires_at);
                CREATE TABLE IF NOT EXISTS miniapp_vehicle_bindings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL UNIQUE,
                    device_id INTEGER NOT NULL UNIQUE,
                    bound_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS miniapp_binding_codes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id INTEGER NOT NULL,
                    code_hash TEXT NOT NULL UNIQUE,
                    expires_at TEXT NOT NULL,
                    used_at TEXT,
                    used_by_user_id INTEGER,
                    created_at TEXT NOT NULL,
                    remote_addr TEXT NOT NULL DEFAULT ''
                );
                CREATE INDEX IF NOT EXISTS idx_miniapp_binding_codes_device_expiry
                    ON miniapp_binding_codes(device_id, expires_at);
                CREATE TABLE IF NOT EXISTS vehicle_trips (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id INTEGER NOT NULL,
                    source_id TEXT NOT NULL,
                    start_ts INTEGER NOT NULL,
                    end_ts INTEGER,
                    distance_km REAL,
                    kwh_consumed REAL,
                    kwh_per_100km REAL,
                    avg_speed_kmh REAL,
                    soc_start INTEGER,
                    soc_end INTEGER,
                    cost REAL,
                    start_name TEXT NOT NULL DEFAULT '',
                    end_name TEXT NOT NULL DEFAULT '',
                    updated_at TEXT NOT NULL,
                    UNIQUE(device_id, source_id)
                );
                CREATE INDEX IF NOT EXISTS idx_vehicle_trips_device_start
                    ON vehicle_trips(device_id, start_ts DESC);
                CREATE TABLE IF NOT EXISTS vehicle_trip_points (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id INTEGER NOT NULL,
                    trip_source_id TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    speed_kmh REAL,
                    updated_at TEXT NOT NULL,
                    UNIQUE(device_id, source_id)
                );
                CREATE INDEX IF NOT EXISTS idx_vehicle_trip_points_trip_time
                    ON vehicle_trip_points(device_id, trip_source_id, timestamp);
                CREATE TABLE IF NOT EXISTS vehicle_charges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id INTEGER NOT NULL,
                    source_id TEXT NOT NULL,
                    start_ts INTEGER NOT NULL,
                    end_ts INTEGER,
                    soc_start INTEGER,
                    soc_end INTEGER,
                    kwh_charged REAL,
                    charge_type TEXT NOT NULL DEFAULT '',
                    cost REAL,
                    lat REAL,
                    lon REAL,
                    status TEXT NOT NULL DEFAULT '',
                    updated_at TEXT NOT NULL,
                    UNIQUE(device_id, source_id)
                );
                CREATE INDEX IF NOT EXISTS idx_vehicle_charges_device_start
                    ON vehicle_charges(device_id, start_ts DESC);
                CREATE TABLE IF NOT EXISTS miniapp_log_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id INTEGER NOT NULL,
                    requested_by_user_id INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    message TEXT NOT NULL DEFAULT '',
                    diagnostic_log_id INTEGER,
                    requested_at TEXT NOT NULL,
                    acknowledged_at TEXT,
                    completed_at TEXT
                );
                CREATE INDEX IF NOT EXISTS idx_miniapp_log_requests_device_status
                    ON miniapp_log_requests(device_id, status, requested_at DESC);
                CREATE TABLE IF NOT EXISTS vehicle_local_sounds (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id INTEGER NOT NULL,
                    sound_key TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    file_size INTEGER NOT NULL DEFAULT 0,
                    modified_at INTEGER NOT NULL DEFAULT 0,
                    synced_at TEXT NOT NULL,
                    UNIQUE(device_id, sound_key)
                );
                CREATE INDEX IF NOT EXISTS idx_vehicle_local_sounds_device_name
                    ON vehicle_local_sounds(device_id, display_name);
                CREATE TABLE IF NOT EXISTS miniapp_remote_commands (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id INTEGER NOT NULL,
                    requested_by_user_id INTEGER NOT NULL,
                    command_type TEXT NOT NULL,
                    payload_json TEXT NOT NULL DEFAULT '{}',
                    status TEXT NOT NULL DEFAULT 'pending',
                    message TEXT NOT NULL DEFAULT '',
                    requested_at TEXT NOT NULL,
                    delivered_at TEXT,
                    completed_at TEXT,
                    expires_at TEXT NOT NULL,
                    result_json TEXT NOT NULL DEFAULT '{}'
                );
                CREATE INDEX IF NOT EXISTS idx_miniapp_remote_commands_device_status
                    ON miniapp_remote_commands(device_id, status, requested_at DESC);
                CREATE INDEX IF NOT EXISTS idx_miniapp_remote_commands_user_requested
                    ON miniapp_remote_commands(requested_by_user_id, requested_at DESC);
                """
            )
            ensure_column(conn, "devices", "trial_started_at", "TEXT")
            ensure_column(conn, "devices", "trial_ends_at", "TEXT")
            for column, definition in (
                ("vehicle_name", "TEXT NOT NULL DEFAULT ''"),
                ("vin_year", "INTEGER"),
                ("model_variant", "TEXT NOT NULL DEFAULT ''"),
                ("car_series", "TEXT NOT NULL DEFAULT ''"),
                ("vehicle_type", "TEXT NOT NULL DEFAULT ''"),
                ("controller_version", "TEXT NOT NULL DEFAULT ''"),
                ("controller_series", "INTEGER NOT NULL DEFAULT 0"),
                ("power_state", "INTEGER"),
                ("runtime_state", "TEXT NOT NULL DEFAULT ''"),
                ("remote_addr", "TEXT NOT NULL DEFAULT ''"),
                ("api_token_hash", "TEXT NOT NULL DEFAULT ''"),
                ("api_token_issued_at", "TEXT"),
                ("soc", "INTEGER"),
                ("electric_range_km", "INTEGER"),
                ("fuel_range_km", "INTEGER"),
                ("gear", "INTEGER"),
                ("lock_state", "INTEGER"),
                ("inside_temp", "INTEGER"),
                ("outside_temp", "INTEGER"),
                ("mileage_km", "REAL"),
                ("voltage_12v", "REAL"),
                ("charging_status", "INTEGER"),
                ("telemetry_updated_at", "TEXT"),
            ):
                ensure_column(conn, "devices", column, definition)
            remove_legacy_vehicle_configuration(conn)
            now = utc_now()
            conn.execute("DELETE FROM miniapp_sessions WHERE expires_at <= ?", (now,))
            conn.execute(
                "DELETE FROM miniapp_binding_codes WHERE expires_at <= ? AND used_at IS NULL",
                (now,),
            )
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
