import base64
import io
import os
import sqlite3
import tempfile
import unittest
import wave
from pathlib import Path
from unittest.mock import patch
from urllib.parse import urlsplit

from cryptography.hazmat.primitives.asymmetric import rsa

_TEST_DATA_DIR = tempfile.TemporaryDirectory()
_TEST_ROOT = Path(_TEST_DATA_DIR.name)
os.environ["WJX_PLATFORM_DATA_DIR"] = str(_TEST_ROOT)
os.environ["WJX_PLATFORM_UPLOAD_DIR"] = str(_TEST_ROOT / "uploads")
os.environ["WJX_PLATFORM_APK_DIR"] = str(_TEST_ROOT / "uploads" / "apk")
os.environ["WJX_PLATFORM_SOUND_DIR"] = str(_TEST_ROOT / "uploads" / "sounds")
os.environ["WJX_PLATFORM_SERVICE_QR_DIR"] = str(_TEST_ROOT / "uploads" / "service_qrcodes")
os.environ["WJX_PLATFORM_LOG_DIR"] = str(_TEST_ROOT / "uploads" / "diagnostic_logs")
os.environ["WJX_PLATFORM_DB"] = str(_TEST_ROOT / "supertoybox.db")
os.environ["WJX_PLATFORM_ADMIN_USER"] = "admin"
os.environ["WJX_PLATFORM_ADMIN_PASSWORD"] = "test-admin-password"
os.environ["WJX_PLATFORM_SECRET_KEY"] = "test-secret-key"
os.environ["WJX_PLATFORM_TRUSTED_PROXY_COUNT"] = "0"

import app as platform_app
import platform_license
import platform_storage


def tearDownModule():
    _TEST_DATA_DIR.cleanup()


class AdminBootstrapSecurityTest(unittest.TestCase):
    def test_platform_secret_key_is_required(self):
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("WJX_PLATFORM_SECRET_KEY", None)
            with self.assertRaisesRegex(RuntimeError, "must be configured"):
                platform_app.configured_secret_key()

    def test_admin_password_is_required(self):
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("WJX_PLATFORM_ADMIN_PASSWORD", None)
            with self.assertRaisesRegex(RuntimeError, "must be configured"):
                platform_app.configured_admin_credentials()

    def test_known_insecure_password_is_rejected(self):
        with patch.dict(
            os.environ,
            {"WJX_PLATFORM_ADMIN_PASSWORD": platform_storage.INSECURE_ADMIN_PASSWORD},
        ):
            with self.assertRaisesRegex(RuntimeError, "known insecure"):
                platform_app.configured_admin_credentials()

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
                platform_app.configured_admin_credentials(),
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
                "activated",
                "trial_started_at",
                "trial_ends_at",
                "first_seen_at",
                "last_seen_at",
                "api_token_hash",
                "api_token_issued_at",
            },
            device_columns,
        )
        self.assertEqual(platform_storage.PLATFORM_SCHEMA_VERSION, schema_version)

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

    def test_invalid_sound_pagination_uses_defaults(self):
        response = self.client.get("/api/v1/sounds?page=invalid&pageSize=invalid")

        self.assertEqual(200, response.status_code)
        self.assertEqual(1, response.json["page"])
        self.assertEqual(30, response.json["pageSize"])

    def test_sound_pagination_is_bounded(self):
        response = self.client.get("/api/v1/sounds?page=-5&pageSize=500")

        self.assertEqual(200, response.status_code)
        self.assertEqual(1, response.json["page"])
        self.assertEqual(50, response.json["pageSize"])

    def test_removed_remote_routes_are_not_registered(self):
        rules = {rule.rule for rule in platform_app.app.url_map.iter_rules()}
        self.assertNotIn("/api/v1/vehicles/heartbeat", rules)
        self.assertNotIn("/api/v1/vehicle-profiles/resolve", rules)
        self.assertFalse(any(rule.startswith("/api/v1/miniapp/") for rule in rules))
        self.assertFalse(any(rule.startswith("/api/v1/vehicles/commands") for rule in rules))
        self.assertNotIn("/api/v1/vehicles/history-sync", rules)
        self.assertIn("/api/v1/logs/upload", rules)



class PlatformWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        platform_app.app.config.update(TESTING=True, SESSION_COOKIE_SECURE=False)
        platform_app.init_db(force=True)

    def setUp(self):
        self.client = platform_app.app.test_client()
        self.device_tokens = {}
        self.reset_platform_state()

    def tearDown(self):
        self.reset_platform_state()

    def reset_platform_state(self):
        with platform_app.db() as conn:
            for table in (
                "releases",
                "sounds",
                "license_codes",
                "devices",
                "diagnostic_logs",
                "audit_logs",
                "api_attempts",
                "login_attempts",
            ):
                conn.execute(f"DELETE FROM {table}")
            conn.execute("UPDATE config SET value = '1' WHERE key IN ('update_enabled', 'sound_library_enabled')")
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
            platform_app.SOUND_DIR,
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
        self.assertTrue(latest.json["forceUpdate"])

        download = self.client.get(urlsplit(latest.json["downloadUrl"]).path)
        self.assertEqual(200, download.status_code)
        self.assertEqual(apk_bytes, download.data)
        download.close()

    def test_sound_upload_is_listed_and_downloadable(self):
        csrf_token = self.login_and_get_csrf()
        wav_buffer = io.BytesIO()
        with wave.open(wav_buffer, "wb") as audio:
            audio.setnchannels(1)
            audio.setsampwidth(1)
            audio.setframerate(8000)
            audio.writeframes(b"\x80" * 800)
        wav_bytes = wav_buffer.getvalue()

        response = self.client.post(
            "/sounds",
            data={
                "name": "Test welcome tone",
                "category": "welcome",
                "tags": "unlock, welcome",
                "description": "Temporary integration test sound",
                "csrf_token": csrf_token,
                "audio": (io.BytesIO(wav_bytes), "test-tone.wav", "audio/wav"),
            },
            content_type="multipart/form-data",
            follow_redirects=False,
        )

        self.assertEqual(302, response.status_code)
        listing = self.client.get("/api/v1/sounds?q=welcome")
        self.assertEqual(200, listing.status_code)
        self.assertEqual(1, listing.json["total"])
        sound = listing.json["items"][0]
        self.assertEqual("Test welcome tone", sound["name"])
        self.assertEqual("welcome", sound["category"])
        self.assertEqual(["unlock", "welcome"], sound["tags"])
        self.assertEqual(100, sound["durationMs"])

        download = self.client.get(urlsplit(sound["downloadUrl"]).path)
        preview = self.client.get(urlsplit(sound["previewUrl"]).path)
        self.assertEqual(200, download.status_code)
        self.assertEqual(200, preview.status_code)
        self.assertEqual(wav_bytes, download.data)
        self.assertEqual(wav_bytes, preview.data)
        download.close()
        preview.close()

        refreshed = self.client.get("/api/v1/sounds?q=welcome")
        self.assertEqual(1, refreshed.json["items"][0]["downloadCount"])

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
                "credentialVersion": 1,
            },
        )
        self.assertEqual(200, initial_status.status_code)
        self.assertFalse(initial_status.json["activated"])
        initial_token = initial_status.json["deviceToken"]

        activation = self.client.post(
            "/api/v1/license/activate",
            json={
                "deviceCode": vin,
                "rawDeviceCode": vin,
                "appVersion": "test",
                "packageName": "com.example.test",
                "activationCode": license_code,
                "credentialVersion": 1,
            },
            headers={"Authorization": f"Bearer {initial_token}"},
        )
        self.assertEqual(200, activation.status_code)
        self.assertTrue(activation.json["success"])
        self.assertTrue(activation.json["activated"])
        self.assertEqual(vin, activation.json["deviceCode"])
        activated_token = activation.json["deviceToken"]
        self.assertNotEqual(initial_token, activated_token)

        activated_status = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "test",
                "packageName": "com.example.test",
                "credentialVersion": 1,
            },
            headers={"Authorization": f"Bearer {activated_token}"},
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

    def register_device(self, vin="L1234567890123456"):
        response = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "1.9",
                "packageName": "com.byd.wjx",
                "credentialVersion": 1,
            },
        )
        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json["deviceToken"])
        self.device_tokens[vin] = response.json["deviceToken"]
        return vin

    def upload_log(self, vin, data=b"07-23 12:00:00.000 I/Test diagnostic\n", filename="wjx.log"):
        token = self.device_tokens.get(vin, "")
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        return self.client.post(
            "/api/v1/logs/upload",
            data={
                "deviceCode": vin,
                "appVersion": "1.9",
                "packageName": "com.byd.wjx",
                "log": (io.BytesIO(data), filename, "application/octet-stream"),
            },
            content_type="multipart/form-data",
            headers=headers,
        )








    def test_signed_offline_license_can_recover_a_lost_device_credential(self):
        vin = self.register_device("LC0FD4C42S7184345")
        original_token = self.device_tokens[vin]
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        proof = {
            "version": 1,
            "signatureAlg": "RS256",
            "deviceCodes": [vin],
            "activated": True,
            "activatedAt": 1_700_000_000_000,
            "issuedAt": 1_700_000_000_000,
            "expiresAt": 0,
            "packageName": "com.byd.wjx",
            "licenseCodeMasked": "STB-TEST-****-TEST",
            "features": [],
        }
        with patch.object(platform_license, "load_offline_license_private_key", return_value=private_key):
            proof["signature"] = platform_app.sign_offline_license(proof)
            recovered = self.client.post(
                "/api/v1/license/status",
                json={
                    "deviceCode": vin,
                    "appVersion": "1.9.2",
                    "packageName": "com.byd.wjx",
                    "credentialVersion": 1,
                    "deviceProof": proof,
                },
            )

        self.assertEqual(200, recovered.status_code)
        self.assertTrue(recovered.json["deviceToken"])
        self.assertNotEqual(original_token, recovered.json["deviceToken"])




    def test_vehicle_pages_use_exact_authorization_categories(self):
        vins = (
            "LC0FD4C42S7184345",
            "LC0FD4C42S7184346",
            "LC0FD4C42S7184347",
        )
        for vin in vins:
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
        self.assertNotIn("销售款型".encode(), detail.data)
        self.assertNotIn("车型配置".encode(), detail.data)

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

    def test_diagnostic_log_upload_requires_the_device_credential(self):
        vin = self.register_device()
        self.device_tokens.pop(vin)

        response = self.upload_log(vin)

        self.assertEqual(401, response.status_code)
        self.assertFalse(response.json["success"])
        self.assertEqual([], list(platform_app.LOG_DIR.iterdir()))

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
