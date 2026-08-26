import base64
import hashlib
import io
import os
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.parse import urlsplit

from cryptography.hazmat.primitives.asymmetric import rsa

_TEST_DATA_DIR = tempfile.TemporaryDirectory()
_TEST_ROOT = Path(_TEST_DATA_DIR.name)
os.environ["WJX_PLATFORM_DATA_DIR"] = str(_TEST_ROOT)
os.environ["WJX_PLATFORM_UPLOAD_DIR"] = str(_TEST_ROOT / "uploads")
os.environ["WJX_PLATFORM_APK_DIR"] = str(_TEST_ROOT / "uploads" / "apk")
os.environ["WJX_PLATFORM_SERVICE_QR_DIR"] = str(_TEST_ROOT / "uploads" / "service_qrcodes")
os.environ["WJX_PLATFORM_LOG_DIR"] = str(_TEST_ROOT / "uploads" / "diagnostic_logs")
os.environ["WJX_PLATFORM_DB"] = str(_TEST_ROOT / "supertoybox.db")
os.environ["WJX_PLATFORM_ADMIN_USER"] = "admin"
os.environ["WJX_PLATFORM_ADMIN_PASSWORD"] = "test-admin-password"
os.environ["WJX_PLATFORM_SECRET_KEY"] = "test-secret-key"
os.environ["WJX_PLATFORM_TRUSTED_PROXY_COUNT"] = "0"

import app as platform_app
import platform_config
import platform_payment
import platform_storage
from platform_apk import ApkMetadata, is_valid_release_version


def tearDownModule():
    _TEST_DATA_DIR.cleanup()


class AdminBootstrapSecurityTest(unittest.TestCase):
    def test_commercial_endpoints_require_clean_credential_free_https(self):
        self.assertEqual(
            "https://wjx.example",
            platform_config.validated_https_base_url(
                "PUBLIC", "https://wjx.example/", allow_path=False
            ),
        )
        self.assertEqual(
            "https://pay.example/api/v2",
            platform_config.validated_https_base_url(
                "PAYMENT", "https://pay.example/api/v2/", allow_path=True
            ),
        )
        for value in (
            "http://wjx.example",
            "https://user:secret@wjx.example",
            "https://wjx.example/api",
            "https://wjx.example?next=evil",
            "https://wjx.example/%2e%2e/admin",
        ):
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                platform_config.validated_https_base_url("PUBLIC", value, allow_path=False)

    def test_release_versions_match_the_android_filename_policy(self):
        self.assertTrue(is_valid_release_version("2.1.20"))
        self.assertTrue(is_valid_release_version("2.1.20-rc.1"))
        self.assertFalse(is_valid_release_version("../../payload"))
        self.assertFalse(is_valid_release_version("2.1.20 beta"))

    def test_platform_secret_key_is_required(self):
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("WJX_PLATFORM_SECRET_KEY", None)
            with self.assertRaisesRegex(RuntimeError, "must be configured"):
                platform_app.configured_secret_key()

    def test_admin_password_is_required(self):
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("WJX_PLATFORM_ADMIN_PASSWORD", None)
            with self.assertRaisesRegex(RuntimeError, "must be configured"):
                platform_storage.configured_admin_credentials()

    def test_known_insecure_password_is_rejected(self):
        with patch.dict(
            os.environ,
            {"WJX_PLATFORM_ADMIN_PASSWORD": platform_storage.INSECURE_ADMIN_PASSWORD},
        ):
            with self.assertRaisesRegex(RuntimeError, "known insecure"):
                platform_storage.configured_admin_credentials()

    def test_explicit_credentials_are_accepted(self):
        with patch.dict(
            os.environ,
            {
                "WJX_PLATFORM_ADMIN_USER": "operator",
                "WJX_PLATFORM_ADMIN_PASSWORD": "commercial-password",
            },
        ):
            self.assertEqual(
                ("operator", "commercial-password"),
                platform_storage.configured_admin_credentials(),
            )


class PlatformStorageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        platform_storage.init_db(force=True)

    def test_fresh_schema_omits_removed_remote_tables(self):
        platform_storage.init_db(force=True)
        with platform_storage.db() as conn:
            tables = {
                row["name"]
                for row in conn.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                ).fetchall()
            }
            schema_version = conn.execute(
                "SELECT schema_version FROM platform_schema WHERE id = 1"
            ).fetchone()[0]
            device_columns = {
                row["name"] for row in conn.execute("PRAGMA table_info(devices)").fetchall()
            }
        self.assertTrue({
            "miniapp_users",
            "miniapp_sessions",
            "miniapp_vehicle_bindings",
            "miniapp_binding_codes",
            "miniapp_log_requests",
            "miniapp_remote_commands",
            "vehicle_local_sounds",
            "vehicle_trips",
            "vehicle_trip_points",
            "vehicle_charges",
        }.isdisjoint(tables))
        self.assertEqual(
            {
                "id",
                "device_code",
                "raw_device_code",
                "app_version",
                "package_name",
                "vehicle_display_name",
                "vin_year",
                "model_variant",
                "car_series",
                "vehicle_type",
                "controller_version",
                "controller_series",
                "activated",
                "trial_started_at",
                "trial_ends_at",
                "first_seen_at",
                "last_seen_at",
            },
            device_columns,
        )
        self.assertEqual(platform_storage.PLATFORM_SCHEMA_VERSION, schema_version)
        with platform_storage.db() as conn:
            config_keys = {
                row["key"] for row in conn.execute("SELECT key FROM config").fetchall()
            }
        self.assertEqual({"license_trial_days", "update_enabled"}, config_keys)

    def test_database_context_commits_closes_and_enables_connection_guards(self):
        with platform_storage.db() as conn:
            busy_timeout = conn.execute("PRAGMA busy_timeout").fetchone()[0]
            foreign_keys = conn.execute("PRAGMA foreign_keys").fetchone()[0]
            conn.execute("CREATE TABLE IF NOT EXISTS connection_guard_test (value TEXT)")
            conn.execute("DELETE FROM connection_guard_test")
            conn.execute("INSERT INTO connection_guard_test (value) VALUES ('committed')")

        self.assertGreaterEqual(busy_timeout, platform_storage.DB_BUSY_TIMEOUT_MS)
        self.assertEqual(1, foreign_keys)
        with self.assertRaises(sqlite3.ProgrammingError):
            conn.execute("SELECT 1")
        with platform_storage.db() as reopened:
            value = reopened.execute("SELECT value FROM connection_guard_test").fetchone()[0]
        self.assertEqual("committed", value)

    def test_unmarked_existing_database_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            legacy_path = os.path.join(directory, "legacy.db")
            with sqlite3.connect(legacy_path) as conn:
                conn.execute("CREATE TABLE devices (id INTEGER PRIMARY KEY)")

            initialized = platform_storage._db_initialized
            try:
                platform_storage._db_initialized = False
                with patch.object(platform_storage, "DB_PATH", Path(legacy_path)):
                    with self.assertRaisesRegex(RuntimeError, "no current schema marker"):
                        platform_storage.init_db(force=True)
            finally:
                platform_storage._db_initialized = initialized

    def test_unsupported_database_schema_version_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            legacy_path = os.path.join(directory, "legacy.db")
            with sqlite3.connect(legacy_path) as conn:
                conn.execute(
                    "CREATE TABLE platform_schema (id INTEGER PRIMARY KEY, schema_version INTEGER)"
                )
                conn.execute("INSERT INTO platform_schema VALUES (1, 0)")

            initialized = platform_storage._db_initialized
            try:
                platform_storage._db_initialized = False
                with patch.object(platform_storage, "DB_PATH", Path(legacy_path)):
                    with self.assertRaisesRegex(RuntimeError, "Unsupported platform database"):
                        platform_storage.init_db(force=True)
            finally:
                platform_storage._db_initialized = initialized

    def test_version_three_database_preserves_vin_activation_during_upgrade(self):
        with tempfile.TemporaryDirectory() as directory:
            legacy_path = os.path.join(directory, "legacy.db")
            with sqlite3.connect(legacy_path) as conn:
                conn.executescript(
                    """
                    CREATE TABLE platform_schema (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        schema_version INTEGER NOT NULL
                    );
                    INSERT INTO platform_schema VALUES (1, 3);
                    CREATE TABLE devices (
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
                    INSERT INTO devices
                        (device_code, raw_device_code, app_version, package_name, activated,
                         first_seen_at, last_seen_at, api_token_hash, api_token_issued_at)
                    VALUES
                        ('LC0FD4C42S7184399', 'LC0FD4C42S7184399', '2.1.19', 'com.byd.wjx', 1,
                         '2026-08-01T00:00:00Z', '2026-08-25T00:00:00Z',
                         'removed-token-hash', '2026-08-01T00:00:00Z');
                    CREATE TABLE license_codes (
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
                    INSERT INTO license_codes
                        (code, device_code, raw_device_code, enabled, activated_at, created_at, updated_at)
                    VALUES
                        ('STB-PRESERVE-VIN-ACTIVATION', 'LC0FD4C42S7184399',
                         'LC0FD4C42S7184399', 1, '2026-08-01T00:00:00Z',
                         '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z');
                    """
                )

            initialized = platform_storage._db_initialized
            try:
                platform_storage._db_initialized = False
                with patch.object(platform_storage, "DB_PATH", Path(legacy_path)):
                    platform_storage.init_db(force=True)
                    with platform_storage.db() as conn:
                        version = conn.execute(
                            "SELECT schema_version FROM platform_schema WHERE id = 1"
                        ).fetchone()[0]
                        device = conn.execute(
                            "SELECT * FROM devices WHERE device_code = ?",
                            ("LC0FD4C42S7184399",),
                        ).fetchone()
                        license_row = conn.execute(
                            "SELECT * FROM license_codes WHERE device_code = ? AND enabled = 1",
                            ("LC0FD4C42S7184399",),
                        ).fetchone()
                        device_columns = {
                            row["name"]
                            for row in conn.execute("PRAGMA table_info(devices)").fetchall()
                        }
                self.assertEqual(platform_storage.PLATFORM_SCHEMA_VERSION, version)
                self.assertEqual("LC0FD4C42S7184399", device["device_code"])
                self.assertIsNotNone(license_row)
                self.assertNotIn("api_token_hash", device_columns)
                self.assertNotIn("api_token_issued_at", device_columns)
                self.assertIn("vehicle_display_name", device_columns)
                self.assertIn("controller_series", device_columns)
            finally:
                platform_storage._db_initialized = initialized

    def test_version_four_database_adds_vehicle_profile_without_losing_vehicle(self):
        with tempfile.TemporaryDirectory() as directory:
            legacy_path = os.path.join(directory, "schema4.db")
            with sqlite3.connect(legacy_path) as conn:
                conn.executescript(
                    """
                    CREATE TABLE platform_schema (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        schema_version INTEGER NOT NULL
                    );
                    INSERT INTO platform_schema VALUES (1, 4);
                    CREATE TABLE devices (
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
                    INSERT INTO devices
                        (device_code, raw_device_code, app_version, package_name, activated,
                         first_seen_at, last_seen_at)
                    VALUES
                        ('LC0FD4C42S7184398', 'LC0FD4C42S7184398', '2.1.20',
                         'com.byd.wjx', 1, '2026-08-01T00:00:00Z',
                         '2026-08-25T00:00:00Z');
                    """
                )

            initialized = platform_storage._db_initialized
            try:
                platform_storage._db_initialized = False
                with patch.object(platform_storage, "DB_PATH", Path(legacy_path)):
                    platform_storage.init_db(force=True)
                    with platform_storage.db() as conn:
                        version = conn.execute(
                            "SELECT schema_version FROM platform_schema WHERE id = 1"
                        ).fetchone()[0]
                        device = conn.execute(
                            "SELECT * FROM devices WHERE device_code = ?",
                            ("LC0FD4C42S7184398",),
                        ).fetchone()
                self.assertEqual(platform_storage.PLATFORM_SCHEMA_VERSION, version)
                self.assertEqual("LC0FD4C42S7184398", device["device_code"])
                self.assertEqual("", device["vehicle_display_name"])
                self.assertEqual(0, device["controller_series"])
            finally:
                platform_storage._db_initialized = initialized

    def test_version_five_database_removes_only_retired_feature_flags(self):
        with tempfile.TemporaryDirectory() as directory:
            legacy_path = os.path.join(directory, "schema5.db")
            with sqlite3.connect(legacy_path) as conn:
                conn.executescript(
                    """
                    CREATE TABLE platform_schema (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        schema_version INTEGER NOT NULL
                    );
                    INSERT INTO platform_schema VALUES (1, 5);
                    CREATE TABLE config (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    );
                    INSERT INTO config VALUES ('license_trial_days', '14');
                    INSERT INTO config VALUES ('update_enabled', '0');
                    INSERT INTO config VALUES ('feature_automation_unlimited', '1');
                    INSERT INTO config VALUES ('feature_cluster_projection', '1');
                    INSERT INTO config VALUES ('sound_library_enabled', '1');
                    CREATE TABLE sounds (id INTEGER PRIMARY KEY, title TEXT NOT NULL);
                    CREATE TABLE vehicle_profiles (id INTEGER PRIMARY KEY, vin TEXT NOT NULL);
                    """
                )

            initialized = platform_storage._db_initialized
            try:
                platform_storage._db_initialized = False
                with patch.object(platform_storage, "DB_PATH", Path(legacy_path)):
                    platform_storage.init_db(force=True)
                    with platform_storage.db() as conn:
                        version = conn.execute(
                            "SELECT schema_version FROM platform_schema WHERE id = 1"
                        ).fetchone()[0]
                        values = {
                            row["key"]: row["value"]
                            for row in conn.execute("SELECT key, value FROM config").fetchall()
                        }
                        tables = {
                            row["name"]
                            for row in conn.execute(
                                "SELECT name FROM sqlite_master WHERE type = 'table'"
                            ).fetchall()
                        }
                self.assertEqual(platform_storage.PLATFORM_SCHEMA_VERSION, version)
                self.assertEqual(
                    {"license_trial_days": "14", "update_enabled": "0"},
                    values,
                )
                self.assertNotIn("sounds", tables)
                self.assertNotIn("vehicle_profiles", tables)
            finally:
                platform_storage._db_initialized = initialized

    def test_version_six_database_repairs_release_metadata_columns(self):
        with tempfile.TemporaryDirectory() as directory:
            legacy_path = os.path.join(directory, "schema6.db")
            with sqlite3.connect(legacy_path) as conn:
                conn.executescript(
                    """
                    CREATE TABLE platform_schema (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        schema_version INTEGER NOT NULL
                    );
                    INSERT INTO platform_schema VALUES (1, 6);
                    CREATE TABLE releases (
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
                    INSERT INTO releases
                        (version, version_code, apk_filename, created_at, updated_at)
                    VALUES ('2.1.19', 59, 'legacy.apk', '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z');
                    """
                )

            initialized = platform_storage._db_initialized
            try:
                platform_storage._db_initialized = False
                with patch.object(platform_storage, "DB_PATH", Path(legacy_path)):
                    platform_storage.init_db(force=True)
                    with platform_storage.db() as conn:
                        version = conn.execute(
                            "SELECT schema_version FROM platform_schema WHERE id = 1"
                        ).fetchone()[0]
                        columns = {
                            row["name"] for row in conn.execute("PRAGMA table_info(releases)").fetchall()
                        }
                        release = conn.execute(
                            "SELECT version, version_code FROM releases WHERE id = 1"
                        ).fetchone()
                self.assertEqual(platform_storage.PLATFORM_SCHEMA_VERSION, version)
                self.assertTrue({"apk_sha256", "package_name", "certificate_sha256"}.issubset(columns))
                self.assertEqual(("2.1.19", 59), tuple(release))
            finally:
                platform_storage._db_initialized = initialized


class PaymentProtocolTest(unittest.TestCase):
    def test_v2_canonicalization_excludes_signature_fields_and_empty_values(self):
        self.assertEqual(
            "money=15.90&out_trade_no=WJX-001&pid=1000&type=wxpay",
            platform_payment.canonicalize({
                "type": "wxpay",
                "sign": "ignored",
                "code": 0,
                "pid": "1000",
                "empty": "",
                "sign_type": "RSA",
                "money": "15.90",
                "out_trade_no": "WJX-001",
            }),
        )

    def test_v2_rsa_sha256_signature_detects_tampering(self):
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        params = {
            "pid": "1000",
            "type": "wxpay",
            "out_trade_no": "WJX-001",
            "money": "15.90",
            "timestamp": "1700000000",
            "sign_type": "RSA",
        }
        params["sign"] = platform_payment.sign_params(params, private_key)
        self.assertTrue(platform_payment.verify_params(params, private_key.public_key()))
        self.assertFalse(platform_payment.verify_params({**params, "money": "0.01"}, private_key.public_key()))

    def test_v2_response_signature_ignores_code_envelope(self):
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        response = {
            "code": 0,
            "msg": "success",
            "trade_no": "20260820195701575389",
            "pay_type": "jump",
            "pay_info": "https://mzf.mapay.cc/pay/20260820195701575389",
            "timestamp": "1787227021",
            "sign_type": "RSA",
        }
        response["sign"] = platform_payment.sign_params(response, private_key)
        self.assertTrue(platform_payment.verify_params(response, private_key.public_key()))
        response["code"] = 1
        self.assertTrue(platform_payment.verify_params(response, private_key.public_key()))


class PlatformRequestSecurityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        platform_app.app.config.update(TESTING=True)
        platform_app.init_db(force=True)

    def setUp(self):
        self.client = platform_app.app.test_client()

    def test_admin_stylesheet_is_served_from_static_assets(self):
        login_page = self.client.get("/login")
        stylesheet = self.client.get("/static/platform.css")
        try:
            self.assertEqual(200, login_page.status_code)
            self.assertIn(b'/static/platform.css', login_page.data)
            self.assertEqual(200, stylesheet.status_code)
            self.assertIn(b":root", stylesheet.data)
        finally:
            login_page.close()
            stylesheet.close()

    def login(self, next_target=None):
        self.client.get("/login")
        with self.client.session_transaction() as session:
            csrf_token = session["csrf_token"]
        path = "/login"
        if next_target is not None:
            path = f"{path}?next={next_target}"
        return self.client.post(
            path,
            data={
                "username": "admin",
                "password": "test-admin-password",
                "csrf_token": csrf_token,
            },
            follow_redirects=False,
        )

    def test_login_rejects_external_next_target(self):
        response = self.login("https://example.com/phishing")

        self.assertEqual(302, response.status_code)
        self.assertEqual("/", response.headers["Location"])

    def test_login_allows_internal_next_target(self):
        response = self.login("/settings")

        self.assertEqual(302, response.status_code)
        self.assertEqual("/settings", response.headers["Location"])

    def test_client_key_ignores_untrusted_forwarded_for(self):
        with platform_app.app.test_request_context(
            "/",
            environ_base={"REMOTE_ADDR": "203.0.113.7"},
            headers={"X-Forwarded-For": "198.51.100.42"},
        ):
            self.assertEqual("203.0.113.7", platform_app.client_key())

    def test_removed_remote_routes_are_not_registered(self):
        rules = {rule.rule for rule in platform_app.app.url_map.iter_rules()}
        self.assertNotIn("/api/v1/vehicles/heartbeat", rules)
        self.assertNotIn("/api/v1/vehicle-profiles/resolve", rules)
        self.assertFalse(any(rule.startswith("/api/v1/miniapp/") for rule in rules))
        self.assertFalse(any(rule.startswith("/api/v1/vehicles/commands") for rule in rules))
        self.assertNotIn("/api/v1/vehicles/history-sync", rules)
        self.assertNotIn("/sounds", rules)
        self.assertFalse(any(rule.startswith("/sounds/") for rule in rules))
        self.assertFalse(any(rule.startswith("/api/v1/sounds") for rule in rules))
        self.assertFalse(any(rule.startswith("/cdn/sounds") for rule in rules))
        self.assertNotIn("/api/v1/config", rules)
        self.assertIn("/api/v1/logs/upload", rules)



class PlatformWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        platform_app.app.config.update(TESTING=True, SESSION_COOKIE_SECURE=False)
        platform_app.init_db(force=True)

    def setUp(self):
        self.client = platform_app.app.test_client()
        self.reset_platform_state()

    def tearDown(self):
        self.reset_platform_state()

    def reset_platform_state(self):
        with platform_app.db() as conn:
            for table in (
                "releases",
                "payment_orders",
                "license_codes",
                "devices",
                "diagnostic_logs",
                "audit_logs",
                "api_attempts",
                "login_attempts",
            ):
                conn.execute(f"DELETE FROM {table}")
            conn.execute("UPDATE config SET value = '1' WHERE key = 'update_enabled'")
            now = platform_app.utc_now()
            for key, (title, subtitle, description) in platform_app.SERVICE_QR_TYPES.items():
                conn.execute(
                    """
                    UPDATE service_qrcodes
                    SET title = ?, subtitle = ?, description = ?, filename = '',
                        original_filename = '', mime_type = '', file_size = 0,
                        enabled = 1, expires_at = '', updated_at = ?
                    WHERE key = ?
                    """,
                    (title, subtitle, description, now, key),
                )
        for directory in (
            platform_app.APK_DIR,
            platform_app.SERVICE_QR_DIR,
            platform_app.LOG_DIR,
        ):
            directory.mkdir(parents=True, exist_ok=True)
            for path in directory.iterdir():
                if path.is_file() or path.is_symlink():
                    path.unlink()

    def login_and_get_csrf(self):
        self.client.get("/login")
        with self.client.session_transaction() as session:
            csrf_token = session["csrf_token"]
        response = self.client.post(
            "/login",
            data={
                "username": "admin",
                "password": "test-admin-password",
                "csrf_token": csrf_token,
            },
            follow_redirects=False,
        )
        self.assertEqual(302, response.status_code)
        with self.client.session_transaction() as session:
            self.assertEqual("admin", session["admin"])
            return session["csrf_token"]

    def test_update_upload_is_available_from_public_api_and_cdn(self):
        csrf_token = self.login_and_get_csrf()
        apk_bytes = b"test-apk-content"

        metadata = ApkMetadata(
            package_name="com.byd.wjx",
            version_name="99.0-test",
            version_code=990000,
            sha256=hashlib.sha256(apk_bytes).hexdigest(),
            certificate_sha256="a" * 64,
        )
        with patch.object(platform_app, "inspect_apk", return_value=metadata):
            response = self.client.post(
                "/updates",
                data={
                    "version": "99.0-test",
                    "version_code": "990000",
                    "release_notes": "Temporary integration test release",
                    "force_update": "on",
                    "csrf_token": csrf_token,
                    "apk": (io.BytesIO(apk_bytes), "fixture.apk"),
                },
                content_type="multipart/form-data",
                follow_redirects=False,
            )

        self.assertEqual(302, response.status_code)
        latest = self.client.get("/api/v1/update/latest")
        self.assertEqual(200, latest.status_code)
        self.assertTrue(latest.json["enabled"])
        self.assertEqual("99.0-test", latest.json["version"])
        self.assertEqual(990000, latest.json["versionCode"])
        self.assertEqual(len(apk_bytes), latest.json["apkSize"])
        self.assertEqual(metadata.sha256, latest.json["sha256"])
        self.assertEqual(metadata.package_name, latest.json["packageName"])
        self.assertEqual(metadata.certificate_sha256, latest.json["certificateSha256"])
        self.assertTrue(latest.json["forceUpdate"])

        download = self.client.get(urlsplit(latest.json["downloadUrl"]).path)
        self.assertEqual(200, download.status_code)
        self.assertEqual(apk_bytes, download.data)
        download.close()

    def test_update_upload_rejects_filename_unsafe_version_before_saving(self):
        csrf_token = self.login_and_get_csrf()
        response = self.client.post(
            "/updates",
            data={
                "version": "../../payload",
                "version_code": "990001",
                "csrf_token": csrf_token,
                "apk": (io.BytesIO(b"not-used"), "fixture.apk"),
            },
            content_type="multipart/form-data",
            follow_redirects=True,
        )
        self.assertEqual(200, response.status_code)
        self.assertIn("版本号格式无效".encode(), response.data)
        with platform_app.db() as conn:
            self.assertEqual(0, conn.execute("SELECT COUNT(*) FROM releases").fetchone()[0])
        self.assertEqual([], list(platform_app.APK_DIR.iterdir()))

    def test_database_backup_is_a_consistent_temporary_snapshot(self):
        with platform_app.db() as conn:
            conn.execute(
                "INSERT INTO audit_logs (type, message, remote_addr, created_at) VALUES (?, ?, '', ?)",
                ("backup-test", "snapshot marker", platform_app.utc_now()),
            )
        self.login_and_get_csrf()
        response = self.client.get("/backup")
        self.assertEqual(200, response.status_code)
        backup_bytes = response.data
        response.close()
        with tempfile.NamedTemporaryFile(suffix=".db") as snapshot:
            snapshot.write(backup_bytes)
            snapshot.flush()
            with sqlite3.connect(snapshot.name) as conn:
                marker = conn.execute(
                    "SELECT message FROM audit_logs WHERE type = 'backup-test' ORDER BY id DESC LIMIT 1"
                ).fetchone()
        self.assertEqual(("snapshot marker",), marker)
        self.assertEqual([], list(platform_storage.DATA_DIR.glob("supertoybox-backup-*.db")))

    def test_service_qr_upload_is_available_from_public_api_and_cdn(self):
        csrf_token = self.login_and_get_csrf()
        png_bytes = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )

        response = self.client.post(
            "/service-qrcodes",
            data={
                "key": "wechat_group",
                "description": "Temporary integration test QR code",
                "expires_at": "2099-12-31 23:59",
                "enabled": "on",
                "csrf_token": csrf_token,
                "image": (io.BytesIO(png_bytes), "wechat-group.png", "image/png"),
            },
            content_type="multipart/form-data",
            follow_redirects=False,
        )

        self.assertEqual(302, response.status_code)
        listing = self.client.get("/api/v1/service/qrcodes")
        self.assertEqual(200, listing.status_code)
        qr_code = next(item for item in listing.json["items"] if item["key"] == "wechat_group")
        self.assertTrue(qr_code["enabled"])
        self.assertEqual("Temporary integration test QR code", qr_code["description"])
        self.assertEqual("2099-12-31 23:59", qr_code["expiresAt"])
        self.assertTrue(qr_code["imageUrl"])

        image = self.client.get(urlsplit(qr_code["imageUrl"]).path)
        self.assertEqual(200, image.status_code)
        self.assertEqual(png_bytes, image.data)
        image.close()

    def test_service_qr_upload_rejects_spoofed_image_extension(self):
        csrf_token = self.login_and_get_csrf()
        response = self.client.post(
            "/service-qrcodes",
            data={
                "key": "wechat",
                "csrf_token": csrf_token,
                "image": (io.BytesIO(b"not-a-real-image"), "fake.png", "image/png"),
            },
            content_type="multipart/form-data",
            follow_redirects=True,
        )
        self.assertEqual(200, response.status_code)
        self.assertIn("文件内容不是有效".encode(), response.data)
        with platform_app.db() as conn:
            row = conn.execute(
                "SELECT filename, mime_type FROM service_qrcodes WHERE key = 'wechat'"
            ).fetchone()
        self.assertEqual("", row["filename"])
        self.assertEqual("", row["mime_type"])

    def test_security_headers_and_admin_scripts_are_csp_compatible(self):
        response = self.client.get("/login")
        self.assertEqual("nosniff", response.headers["X-Content-Type-Options"])
        self.assertEqual("DENY", response.headers["X-Frame-Options"])
        self.assertEqual("no-referrer", response.headers["Referrer-Policy"])
        csp = response.headers["Content-Security-Policy"]
        self.assertIn("frame-ancestors 'none'", csp)
        self.assertNotIn("'unsafe-inline'", csp)
        self.assertIn(b'/static/platform.js', response.data)
        self.assertNotIn(b"onclick=", response.data)
        self.assertNotIn(b"onsubmit=", response.data)

    def test_license_activation_binds_code_to_one_vin(self):
        license_code = "STB-ABCD-EFGH-JKLM-NPQR-STUV"
        vin = "L1234567890123456"
        other_vin = "L6543210987654321"
        now = platform_app.utc_now()
        with platform_app.db() as conn:
            conn.execute(
                """
                INSERT INTO license_codes (code, note, enabled, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?)
                """,
                (license_code, "Temporary integration test code", now, now),
            )

        initial_status = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "test",
                "packageName": "com.example.test",
            },
        )
        self.assertEqual(200, initial_status.status_code)
        self.assertFalse(initial_status.json["activated"])

        activation = self.client.post(
            "/api/v1/license/activate",
            json={
                "deviceCode": vin,
                "rawDeviceCode": vin,
                "appVersion": "test",
                "packageName": "com.example.test",
                "activationCode": license_code,
            },
        )
        self.assertEqual(200, activation.status_code)
        self.assertTrue(activation.json["success"])
        self.assertTrue(activation.json["activated"])
        self.assertEqual(vin, activation.json["deviceCode"])
        self.assertNotIn("deviceToken", activation.json)
        self.assertNotIn("offlineLicense", activation.json)

        activated_status = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "test",
                "packageName": "com.example.test",
            },
        )
        self.assertEqual(200, activated_status.status_code)
        self.assertTrue(activated_status.json["activated"])
        self.assertGreater(activated_status.json["activatedAt"], 0)

        duplicate_activation = self.client.post(
            "/api/v1/license/activate",
            json={
                "deviceCode": other_vin,
                "rawDeviceCode": other_vin,
                "appVersion": "test",
                "packageName": "com.example.test",
                "activationCode": license_code,
            },
        )
        self.assertEqual(409, duplicate_activation.status_code)
        self.assertFalse(duplicate_activation.json["activated"])

    def test_activation_ignores_client_supplied_vin_aliases(self):
        license_code = "STB-AAAA-BBBB-CCCC-DDDD-EEEE"
        primary_vin = "LC0FD4C42S7184321"
        attacker_vin = "LC0FD4C42S7184322"
        now = platform_app.utc_now()
        with platform_app.db() as conn:
            conn.execute(
                """
                INSERT INTO license_codes (code, note, enabled, created_at, updated_at)
                VALUES (?, '', 1, ?, ?)
                """,
                (license_code, now, now),
            )
        activation = self.client.post(
            "/api/v1/license/activate",
            json={
                "deviceCode": primary_vin,
                "rawDeviceCode": primary_vin,
                "deviceCodes": [primary_vin, attacker_vin],
                "rawDeviceCodes": [primary_vin, attacker_vin],
                "appVersion": "2.1.20",
                "packageName": "com.byd.wjx",
                "activationCode": license_code,
            },
        )
        self.assertEqual(200, activation.status_code)
        self.assertEqual(primary_vin, activation.json["deviceCode"])
        self.assertNotIn("offlineLicense", activation.json)
        with platform_app.db() as conn:
            license_row = conn.execute(
                "SELECT device_code FROM license_codes WHERE code = ?",
                (license_code,),
            ).fetchone()
            attacker = conn.execute(
                "SELECT id FROM devices WHERE device_code = ?",
                (attacker_vin,),
            ).fetchone()
        self.assertEqual(primary_vin, license_row["device_code"])
        self.assertIsNone(attacker)

    def register_device(self, vin="L1234567890123456", vehicle_profile=None):
        payload = {
            "deviceCode": vin,
            "appVersion": "1.9",
            "packageName": "com.byd.wjx",
        }
        if vehicle_profile is not None:
            payload["vehicleProfile"] = vehicle_profile
        response = self.client.post(
            "/api/v1/license/status",
            json=payload,
        )
        self.assertEqual(200, response.status_code)
        self.assertNotIn("deviceToken", response.json)
        return vin

    def test_vehicle_profile_upload_retains_last_known_non_empty_values(self):
        vin = "LC0FD4C42S7184388"
        self.register_device(
            vin,
            {
                "displayName": "宋 PLUS DM-i",
                "vinYear": 2025,
                "modelVariant": "SA3HEV",
                "carSeries": "SONG_PLUS",
                "vehicleType": "PHEV",
                "controllerVersion": "57.1.3.2501010.1",
                "controllerSeries": 57,
            },
        )
        self.register_device(vin, {})

        with platform_app.db() as conn:
            device = conn.execute(
                "SELECT * FROM devices WHERE device_code = ?",
                (vin,),
            ).fetchone()

        self.assertEqual("宋 PLUS DM-i", device["vehicle_display_name"])
        self.assertEqual(2025, device["vin_year"])
        self.assertEqual("SA3HEV", device["model_variant"])
        self.assertEqual("SONG_PLUS", device["car_series"])
        self.assertEqual("PHEV", device["vehicle_type"])
        self.assertEqual("57.1.3.2501010.1", device["controller_version"])
        self.assertEqual(57, device["controller_series"])

    def create_payment(self, vin, payment_type="wxpay"):
        return self.client.post(
            "/api/v1/license/payment/create",
            json={
                "deviceCode": vin,
                "rawDeviceCode": vin,
                "deviceCodes": [vin],
                "appVersion": "2.2-test",
                "packageName": "com.byd.wjx",
                "paymentType": payment_type,
            },
        )

    def payment_provider_create_result(self, trade_no="PLATFORM-001"):
        return {
            "code": 0,
            "msg": "success",
            "trade_no": trade_no,
            "pay_type": "qrcode",
            "pay_info": "weixin://wxpay/test-order",
            "timestamp": "1700000000",
            "sign": "verified-by-platform-client",
            "sign_type": "RSA",
        }

    def test_payment_checkout_mode_rejects_insecure_or_mismatched_urls(self):
        self.assertEqual("", platform_app.payment_checkout_mode("wxpay", "http://pay.example/order"))
        self.assertEqual("", platform_app.payment_checkout_mode("wxpay", "javascript:alert(1)"))
        self.assertEqual("", platform_app.payment_checkout_mode("alipay", "weixin://wxpay/order"))
        self.assertEqual("hosted", platform_app.payment_checkout_mode("alipay", "https://pay.example/order"))
        self.assertEqual("qrcode", platform_app.payment_checkout_mode("wxpay", "weixin://wxpay/order"))

    def test_unactivated_device_can_create_payment_without_device_credential(self):
        vin = "LC0FD4C42S7184301"
        initial = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "2.1.20",
                "packageName": "com.byd.wjx",
            },
        )
        self.assertEqual(200, initial.status_code)
        self.assertNotIn("deviceToken", initial.json)
        with patch.object(platform_app, "payment_ready", return_value=True), patch.object(
            platform_app,
            "create_provider_order",
            return_value=self.payment_provider_create_result("PLATFORM-POLL-TOKEN"),
        ) as create_provider:
            created = self.create_payment(vin)
        self.assertEqual(200, created.status_code)
        self.assertTrue(created.json["orderNo"].startswith("WJX"))
        self.assertTrue(created.json["pollToken"])
        self.assertEqual("weixin://wxpay/test-order", created.json["payUrl"])
        self.assertEqual("qrcode", created.json["checkoutMode"])
        create_provider.assert_called_once()
        provider_request = create_provider.call_args.args[0]
        self.assertEqual(
            "https://wjx.689588.xyz/api/v1/license/payment/notify",
            provider_request["notify_url"],
        )
        self.assertTrue(
            provider_request["return_url"].startswith(
                "https://wjx.689588.xyz/api/v1/license/payment/return?out_trade_no="
            )
        )
        rejected = self.client.post(
            "/api/v1/license/payment/status",
            json={"orderNo": created.json["orderNo"], "pollToken": "wrong-token"},
        )
        self.assertEqual(403, rejected.status_code)

    def test_status_contract_has_no_device_credentials(self):
        vin = "LC0FD4C42S7184310"
        first = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "2.1.20",
                "packageName": "com.byd.wjx",
            },
        )
        second = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "2.1.20",
                "packageName": "com.byd.wjx",
            },
        )
        self.assertEqual(200, first.status_code)
        self.assertEqual(200, second.status_code)
        self.assertFalse(first.json["activated"])
        self.assertFalse(second.json["activated"])
        self.assertNotIn("deviceToken", first.json)
        self.assertNotIn("deviceToken", second.json)
        with platform_app.db() as conn:
            columns = {
                row["name"] for row in conn.execute("PRAGMA table_info(devices)").fetchall()
            }
        self.assertNotIn("api_token_hash", columns)
        self.assertNotIn("api_token_issued_at", columns)

    def test_activated_device_restores_directly_by_vin(self):
        vin = self.register_device("LC0FD4C42S7184309")
        now = platform_app.utc_now()
        with platform_app.db() as conn:
            conn.execute(
                """
                INSERT INTO license_codes
                    (code, device_code, raw_device_code, note, enabled, activated_at, created_at, updated_at)
                VALUES (?, ?, ?, '', 1, ?, ?, ?)
                """,
                ("STB-TEST-NO-RECOVERY", vin, vin, now, now, now),
            )

        restored = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "2.1.18",
                "packageName": "com.byd.wjx",
            },
        )

        self.assertEqual(200, restored.status_code)
        self.assertTrue(restored.json["activated"])
        self.assertGreater(restored.json["activatedAt"], 0)
        self.assertNotIn("deviceToken", restored.json)
        self.assertNotIn("offlineLicense", restored.json)

    def test_pending_payment_reuses_order_until_local_expiry(self):
        vin = self.register_device("LC0FD4C42S7184302")
        with patch.object(platform_app, "payment_ready", return_value=True), patch.object(
            platform_app,
            "create_provider_order",
            return_value=self.payment_provider_create_result(),
        ) as create_provider:
            first = self.create_payment(vin)
            second = self.create_payment(vin)
        self.assertEqual(200, first.status_code)
        self.assertEqual(200, second.status_code)
        self.assertEqual(first.json["orderNo"], second.json["orderNo"])
        self.assertNotEqual(first.json["pollToken"], second.json["pollToken"])
        create_provider.assert_called_once()
        with platform_app.db() as conn:
            self.assertEqual(1, conn.execute("SELECT COUNT(*) FROM payment_orders").fetchone()[0])

    def test_expired_pending_payment_creates_fresh_provider_order(self):
        vin = self.register_device("LC0FD4C42S7184305")
        provider_results = [
            self.payment_provider_create_result("PLATFORM-001") | {"pay_info": "https://mzf.mapay.cc/pay/first"},
            self.payment_provider_create_result("PLATFORM-002") | {"payurl": "https://mzf.mapay.cc/pay/second"},
        ]
        with patch.object(platform_app, "payment_ready", return_value=True), patch.object(
            platform_app,
            "create_provider_order",
            side_effect=provider_results,
        ) as create_provider:
            first = self.create_payment(vin)
            with platform_app.db() as conn:
                conn.execute(
                    "UPDATE payment_orders SET expires_at = ? WHERE out_trade_no = ?",
                    ("2000-01-01T00:00:00Z", first.json["orderNo"]),
                )
            second = self.create_payment(vin)
        self.assertEqual(200, first.status_code)
        self.assertEqual(200, second.status_code)
        self.assertNotEqual(first.json["orderNo"], second.json["orderNo"])
        self.assertEqual("https://mzf.mapay.cc/pay/second", second.json["payInfo"])
        self.assertEqual("https://mzf.mapay.cc/pay/second", second.json["payUrl"])
        self.assertEqual("hosted", second.json["checkoutMode"])
        self.assertEqual(2, create_provider.call_count)
        with platform_app.db() as conn:
            self.assertEqual(2, conn.execute("SELECT COUNT(*) FROM payment_orders").fetchone()[0])

    def test_new_payment_orders_are_rate_limited_without_device_tokens(self):
        vins = [
            "LC0FD4C42S7184401",
            "LC0FD4C42S7184402",
            "LC0FD4C42S7184403",
        ]
        with patch.object(platform_app, "PAYMENT_CREATE_IP_LIMIT", 2), patch.object(
            platform_app,
            "PAYMENT_CREATE_DEVICE_LIMIT",
            10,
        ), patch.object(platform_app, "payment_ready", return_value=True), patch.object(
            platform_app,
            "create_provider_order",
            return_value=self.payment_provider_create_result(),
        ) as create_provider:
            responses = [self.create_payment(vin) for vin in vins]
        self.assertEqual([200, 200, 429], [response.status_code for response in responses])
        self.assertEqual(2, create_provider.call_count)
        self.assertNotIn("deviceToken", responses[0].json)

    def test_payment_provider_failure_does_not_expose_endpoint_details(self):
        vin = "LC0FD4C42S7184404"
        with patch.object(platform_app, "payment_ready", return_value=True), patch.object(
            platform_app,
            "create_provider_order",
            side_effect=platform_app.PaymentProviderError(
                "GET https://secret-provider.example/order failed at 203.0.113.8:443"
            ),
        ):
            response = self.create_payment(vin)
        self.assertEqual(502, response.status_code)
        self.assertEqual("创建支付订单失败，请稍后重试", response.json["message"])
        self.assertNotIn("secret-provider.example", response.get_data(as_text=True))
        self.assertNotIn("203.0.113.8", response.get_data(as_text=True))
        with platform_app.db() as conn:
            audit = conn.execute(
                "SELECT message FROM audit_logs WHERE type = 'payment' ORDER BY id DESC LIMIT 1"
            ).fetchone()["message"]
        self.assertNotIn("secret-provider.example", audit)
        self.assertNotIn("203.0.113.8", audit)

    def test_legacy_order_uses_current_ttl_for_reuse_decision(self):
        vin = self.register_device("LC0FD4C42S7184306")
        with patch.object(platform_app, "payment_ready", return_value=True), patch.object(
            platform_app,
            "create_provider_order",
            return_value=self.payment_provider_create_result(),
        ) as create_provider:
            first = self.create_payment(vin)
            with platform_app.db() as conn:
                old_created = (
                    platform_app.utc_datetime()
                    - platform_app.timedelta(seconds=301)
                ).replace(microsecond=0).isoformat() + "Z"
                conn.execute(
                    "UPDATE payment_orders SET created_at = ?, expires_at = ? WHERE out_trade_no = ?",
                    (old_created, "2099-01-01T00:00:00Z", first.json["orderNo"]),
                )
            second = self.create_payment(vin)
        self.assertEqual(200, second.status_code)
        self.assertNotEqual(first.json["orderNo"], second.json["orderNo"])
        self.assertEqual(2, create_provider.call_count)

    def test_verified_paid_query_activates_once_and_wrong_poll_token_is_rejected(self):
        vin = self.register_device("LC0FD4C42S7184303")
        with patch.object(platform_app, "PAYMENT_PID", "1000"), patch.object(
            platform_app,
            "payment_ready",
            return_value=True,
        ), patch.object(
            platform_app,
            "create_provider_order",
            return_value=self.payment_provider_create_result(),
        ):
            created = self.create_payment(vin)
        self.assertEqual(200, created.status_code)
        order_no = created.json["orderNo"]
        status_payload = {"orderNo": order_no, "pollToken": created.json["pollToken"]}
        rejected = self.client.post(
            "/api/v1/license/payment/status",
            json={"orderNo": order_no, "pollToken": "wrong-token"},
        )
        self.assertEqual(403, rejected.status_code)

        provider_paid = {
            "code": 0,
            "msg": "success",
            "trade_no": "PLATFORM-001",
            "out_trade_no": order_no,
            "status": 1,
            "pid": "1000",
            "money": "15.90",
            "timestamp": "1700000001",
            "sign": "verified-by-platform-client",
            "sign_type": "RSA",
        }
        with patch.object(platform_app, "PAYMENT_PID", "1000"), patch.object(
            platform_app,
            "query_provider_order",
            return_value=provider_paid,
        ) as query_provider:
            paid = self.client.post(
                "/api/v1/license/payment/status",
                json=status_payload,
            )
            repeated = self.client.post(
                "/api/v1/license/payment/status",
                json=status_payload,
            )
        self.assertEqual(200, paid.status_code)
        self.assertTrue(paid.json["paid"])
        self.assertTrue(paid.json["activated"])
        self.assertEqual(200, repeated.status_code)
        self.assertTrue(repeated.json["activated"])
        query_provider.assert_called_once()
        with platform_app.db() as conn:
            self.assertEqual(1, conn.execute("SELECT COUNT(*) FROM license_codes").fetchone()[0])
            self.assertEqual("paid", conn.execute("SELECT status FROM payment_orders").fetchone()[0])

    def test_wrong_payment_amount_and_forged_notification_do_not_activate(self):
        vin = self.register_device("LC0FD4C42S7184304")
        with patch.object(platform_app, "PAYMENT_PID", "1000"), patch.object(
            platform_app,
            "payment_ready",
            return_value=True,
        ), patch.object(
            platform_app,
            "create_provider_order",
            return_value=self.payment_provider_create_result(),
        ):
            created = self.create_payment(vin)
        order_no = created.json["orderNo"]
        wrong_amount = {
            "code": 0,
            "msg": "success",
            "trade_no": "PLATFORM-002",
            "out_trade_no": order_no,
            "status": 1,
            "pid": "1000",
            "money": "0.01",
            "timestamp": "1700000002",
            "sign": "verified-by-platform-client",
            "sign_type": "RSA",
        }
        with patch.object(platform_app, "PAYMENT_PID", "1000"), patch.object(
            platform_app,
            "query_provider_order",
            return_value=wrong_amount,
        ):
            pending = self.client.post(
                "/api/v1/license/payment/status",
                json={"orderNo": order_no, "pollToken": created.json["pollToken"]},
            )
        self.assertEqual(200, pending.status_code)
        self.assertFalse(pending.json["paid"])
        with patch.object(platform_app, "verify_payment_params", return_value=False):
            forged = self.client.post(
                "/api/v1/license/payment/notify",
                data={"pid": "1000", "out_trade_no": order_no, "money": "15.90", "sign": "forged"},
            )
        self.assertEqual(400, forged.status_code)
        with platform_app.db() as conn:
            self.assertEqual(0, conn.execute("SELECT COUNT(*) FROM license_codes").fetchone()[0])
            self.assertEqual("pending", conn.execute("SELECT status FROM payment_orders").fetchone()[0])

    def upload_log(self, vin, data=b"07-23 12:00:00.000 I/Test diagnostic\n", filename="wjx.log"):
        return self.client.post(
            "/api/v1/logs/upload",
            data={
                "deviceCode": vin,
                "appVersion": "1.9",
                "packageName": "com.byd.wjx",
                "log": (io.BytesIO(data), filename, "application/octet-stream"),
            },
            content_type="multipart/form-data",
        )








    def test_vin_restores_after_reinstall_without_local_proof(self):
        vin = self.register_device("LC0FD4C42S7184345")
        now = platform_app.utc_now()
        with platform_app.db() as conn:
            conn.execute(
                """
                INSERT INTO license_codes
                    (code, device_code, raw_device_code, note, enabled, activated_at, created_at, updated_at)
                VALUES (?, ?, ?, '', 1, ?, ?, ?)
                """,
                ("STB-OFFLINE-RECOVERY-0001", vin, vin, now, now, now),
            )
            platform_app.refresh_cached_device_activation(conn, vin)
        recovered = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "2.1.20",
                "packageName": "com.byd.wjx",
            },
        )

        self.assertEqual(200, recovered.status_code)
        self.assertTrue(recovered.json["activated"])
        self.assertNotIn("deviceToken", recovered.json)
        self.assertNotIn("offlineLicense", recovered.json)




    def test_vehicle_pages_use_exact_authorization_categories(self):
        vins = (
            "LC0FD4C42S7184345",
            "LC0FD4C42S7184346",
            "LC0FD4C42S7184347",
        )
        self.register_device(
            vins[0],
            {
                "displayName": "宋 PLUS DM-i",
                "vinYear": 2025,
                "modelVariant": "SA3HEV",
                "carSeries": "SONG_PLUS",
                "vehicleType": "PHEV",
                "controllerVersion": "57.1.3.2501010.1",
                "controllerSeries": 57,
            },
        )
        for vin in vins[1:]:
            self.register_device(vin)
        with platform_app.db() as conn:
            now = platform_app.utc_now()
            conn.execute(
                """
                INSERT INTO license_codes
                    (code, device_code, raw_device_code, note, enabled, activated_at, created_at, updated_at)
                VALUES (?, ?, ?, '', 1, ?, ?, ?)
                """,
                ("STB-TEST-AUTH-CATEGORY", vins[0], vins[0], now, now, now),
            )
            platform_app.refresh_cached_device_activation(conn, vins[0])
            conn.execute(
                "UPDATE devices SET trial_started_at = '2020-01-01T00:00:00Z', "
                "trial_ends_at = '2020-01-08T00:00:00Z' WHERE device_code = ?",
                (vins[2],),
            )
            device_id = conn.execute(
                "SELECT id FROM devices WHERE device_code = ?",
                (vins[0],),
            ).fetchone()["id"]

        self.login_and_get_csrf()
        activated = self.client.get("/vehicles?status=activated")
        trial = self.client.get("/vehicles?status=trial")
        expired = self.client.get("/vehicles?status=expired")
        detail = self.client.get(f"/vehicles/{device_id}")

        self.assertEqual(200, activated.status_code)
        self.assertEqual(200, trial.status_code)
        self.assertEqual(200, expired.status_code)
        self.assertIn(vins[0].encode(), activated.data)
        self.assertNotIn(vins[1].encode(), activated.data)
        self.assertIn(vins[1].encode(), trial.data)
        self.assertIn(vins[2].encode(), expired.data)
        self.assertIn("已激活".encode(), activated.data)
        self.assertIn("试用".encode(), activated.data)
        self.assertIn("已到期".encode(), activated.data)
        self.assertIn(vins[0].encode(), detail.data)
        self.assertIn("最近诊断日志".encode(), detail.data)
        self.assertIn("车型配置".encode(), detail.data)
        self.assertIn("2025款 宋 PLUS DM-i".encode(), detail.data)
        self.assertIn("SA3HEV".encode(), detail.data)
        self.assertIn("SONG_PLUS".encode(), detail.data)
        self.assertIn("PHEV".encode(), detail.data)
        self.assertIn("57.1.3.2501010.1".encode(), detail.data)

    def test_vehicle_detail_has_no_device_credential_controls(self):
        vin = self.register_device("LC0FD4C42S7184311")
        with platform_app.db() as conn:
            device_id = conn.execute(
                "SELECT id FROM devices WHERE device_code = ?",
                (vin,),
            ).fetchone()["id"]

        self.login_and_get_csrf()
        detail = self.client.get(f"/vehicles/{device_id}")

        self.assertEqual(200, detail.status_code)
        self.assertNotIn("设备凭证".encode(), detail.data)
        self.assertNotIn("重置设备凭证".encode(), detail.data)
        rules = {rule.rule for rule in platform_app.app.url_map.iter_rules()}
        self.assertNotIn("/vehicles/<int:device_id>/reset-credential", rules)

    def test_disabling_or_deleting_a_license_immediately_removes_activated_status(self):
        vin = self.register_device("LC0FD4C42S7184345")
        now = platform_app.utc_now()
        with platform_app.db() as conn:
            cursor = conn.execute(
                """
                INSERT INTO license_codes
                    (code, device_code, raw_device_code, note, enabled, activated_at, created_at, updated_at)
                VALUES (?, ?, ?, '', 1, ?, ?, ?)
                """,
                ("STB-TEST-TOGGLE-DELETE", vin, vin, now, now, now),
            )
            code_id = cursor.lastrowid
            platform_app.refresh_cached_device_activation(conn, vin)

        csrf_token = self.login_and_get_csrf()
        disabled = self.client.post(
            f"/licenses/{code_id}/toggle",
            data={"csrf_token": csrf_token},
        )
        self.assertEqual(302, disabled.status_code)
        with platform_app.db() as conn:
            device = conn.execute("SELECT * FROM devices WHERE device_code = ?", (vin,)).fetchone()
            self.assertFalse(platform_app.device_authorization(conn, device)["activated"])
            self.assertEqual(0, device["activated"])

        enabled = self.client.post(
            f"/licenses/{code_id}/toggle",
            data={"csrf_token": csrf_token},
        )
        self.assertEqual(302, enabled.status_code)
        deleted = self.client.post(
            f"/licenses/{code_id}/delete",
            data={"csrf_token": csrf_token},
        )
        self.assertEqual(302, deleted.status_code)
        with platform_app.db() as conn:
            device = conn.execute("SELECT * FROM devices WHERE device_code = ?", (vin,)).fetchone()
            self.assertFalse(platform_app.device_authorization(conn, device)["activated"])
            self.assertEqual(0, device["activated"])

    def test_diagnostic_log_upload_requires_registered_vehicle(self):
        response = self.upload_log("L1234567890123456")

        self.assertEqual(403, response.status_code)
        self.assertFalse(response.json["success"])
        self.assertEqual([], list(platform_app.LOG_DIR.iterdir()))

    def test_diagnostic_log_upload_accepts_registered_vin_without_device_credential(self):
        vin = self.register_device()

        response = self.upload_log(vin)

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json["success"])
        self.assertEqual(1, len(list(platform_app.LOG_DIR.iterdir())))

    def test_diagnostic_log_upload_is_private_downloadable_and_deletable(self):
        vin = self.register_device()
        log_bytes = "07-23 12:00:00.000 E/Test 中文异常\n".encode("utf-8")

        upload = self.upload_log(vin, log_bytes)
        self.assertEqual(200, upload.status_code)
        self.assertTrue(upload.json["success"])
        log_id = upload.json["logId"]

        private_view = self.client.get(f"/diagnostic-logs/{log_id}/view")
        self.assertEqual(302, private_view.status_code)
        self.assertTrue(private_view.headers["Location"].startswith("/login"))

        csrf_token = self.login_and_get_csrf()
        listing = self.client.get("/diagnostic-logs")
        self.assertEqual(200, listing.status_code)
        self.assertIn(vin.encode("utf-8"), listing.data)

        view = self.client.get(f"/diagnostic-logs/{log_id}/view")
        download = self.client.get(f"/diagnostic-logs/{log_id}/download")
        self.assertEqual(log_bytes, view.data)
        self.assertEqual(log_bytes, download.data)
        self.assertIn("attachment", download.headers["Content-Disposition"])
        view.close()
        download.close()

        deleted = self.client.post(
            f"/diagnostic-logs/{log_id}/delete",
            data={"csrf_token": csrf_token},
            follow_redirects=False,
        )
        self.assertEqual(302, deleted.status_code)
        with platform_app.db() as conn:
            self.assertIsNone(
                conn.execute("SELECT id FROM diagnostic_logs WHERE id = ?", (log_id,)).fetchone()
            )
        self.assertEqual([], list(platform_app.LOG_DIR.iterdir()))

    def test_diagnostic_log_hides_server_endpoints_on_upload_view_and_download(self):
        vin = self.register_device()
        log_bytes = (
            b"GET https://wjx.689588.xyz/private failed via 203.0.113.8:443\n"
            b"wjx.689588.xyz unavailable via 2001:db8::8; "
            b"controller=57.1.3.2501010.1 time=12:00:00.000\n"
        )

        upload = self.upload_log(vin, log_bytes)
        self.assertEqual(200, upload.status_code)
        log_id = upload.json["logId"]
        stored = next(platform_app.LOG_DIR.iterdir()).read_bytes()
        self.assertNotIn(b"689588.xyz", stored)
        self.assertNotIn(b"203.0.113.8", stored)
        self.assertNotIn(b"2001:db8::8", stored)
        self.assertIn(b"57.1.3.2501010.1", stored)
        self.assertIn(b"12:00:00.000", stored)

        self.login_and_get_csrf()
        view = self.client.get(f"/diagnostic-logs/{log_id}/view")
        download = self.client.get(f"/diagnostic-logs/{log_id}/download")
        for response in (view, download):
            self.assertNotIn(b"689588.xyz", response.data)
            self.assertNotIn(b"203.0.113.8", response.data)
            self.assertNotIn(b"2001:db8::8", response.data)
            self.assertIn(b"[server endpoint hidden]", response.data)
            self.assertIn(b"57.1.3.2501010.1", response.data)
            self.assertIn(b"12:00:00.000", response.data)

    def test_diagnostic_log_view_scrubs_historical_files(self):
        vin = self.register_device()
        upload = self.upload_log(vin)
        log_id = upload.json["logId"]
        path = next(platform_app.LOG_DIR.iterdir())
        path.write_bytes(b"legacy https://wjx.689588.xyz/path 198.51.100.9\n")

        self.login_and_get_csrf()
        view = self.client.get(f"/diagnostic-logs/{log_id}/view")

        self.assertNotIn(b"689588.xyz", view.data)
        self.assertNotIn(b"198.51.100.9", view.data)
        self.assertIn(b"[server endpoint hidden]", view.data)

    def test_diagnostic_log_accepts_raw_bytes_but_rejects_wrong_extension_and_oversize(self):
        vin = self.register_device()

        wrong_extension = self.upload_log(vin, b"plain text", "payload.apk")
        self.assertEqual(400, wrong_extension.status_code)

        raw_bytes = b"text\x00binary\xff\xfe"
        raw_upload = self.upload_log(vin, raw_bytes, "wjx.log")
        self.assertEqual(200, raw_upload.status_code)
        log_id = raw_upload.json["logId"]
        self.login_and_get_csrf()
        download = self.client.get(f"/diagnostic-logs/{log_id}/download")
        self.assertEqual(raw_bytes, download.data)
        download.close()

        with patch.object(platform_app, "MAX_LOG_SIZE", 4):
            oversize = self.upload_log(vin, b"12345", "wjx.log")
        self.assertEqual(413, oversize.status_code)
        self.assertEqual(1, len(list(platform_app.LOG_DIR.iterdir())))









if __name__ == "__main__":
    unittest.main()
