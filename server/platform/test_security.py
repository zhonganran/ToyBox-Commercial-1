import base64
import io
import os
import sqlite3
import tempfile
import unittest
import wave
from unittest.mock import patch
from urllib.parse import urlsplit

from cryptography.hazmat.primitives.asymmetric import rsa

_TEST_DATA_DIR = tempfile.TemporaryDirectory()
os.environ["WJX_PLATFORM_DATA_DIR"] = _TEST_DATA_DIR.name
os.environ["WJX_PLATFORM_ADMIN_USER"] = "admin"
os.environ["WJX_PLATFORM_ADMIN_PASSWORD"] = "test-admin-password"
os.environ["WJX_PLATFORM_SECRET_KEY"] = "test-secret-key"
os.environ["WJX_PLATFORM_TRUSTED_PROXY_COUNT"] = "0"

import app as platform_app
import platform_license
import platform_miniapp
import platform_miniapp_api
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

    def test_legacy_default_password_is_rejected(self):
        with patch.dict(
            os.environ,
            {"WJX_PLATFORM_ADMIN_PASSWORD": platform_app.LEGACY_DEFAULT_ADMIN_PASSWORD},
        ):
            with self.assertRaisesRegex(RuntimeError, "legacy default"):
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


class PlatformStorageMigrationTest(unittest.TestCase):
    def test_legacy_vehicle_configuration_cleanup_preserves_identity_and_credentials(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        definitions = []
        for column in platform_storage.DEVICE_SCHEMA_COLUMNS:
            if column == "id":
                definition = "id INTEGER PRIMARY KEY AUTOINCREMENT"
            elif column == "device_code":
                definition = "device_code TEXT NOT NULL UNIQUE"
            elif column in {"activated", "controller_series"}:
                definition = f"{column} INTEGER NOT NULL DEFAULT 0"
            elif column in {"vin_year", "power_state"}:
                definition = f"{column} INTEGER"
            elif column in {"trial_started_at", "trial_ends_at", "api_token_issued_at"}:
                definition = f"{column} TEXT"
            else:
                definition = f"{column} TEXT NOT NULL DEFAULT ''"
            definitions.append(definition)
        definitions.append("sales_model TEXT NOT NULL DEFAULT ''")
        conn.execute(f"CREATE TABLE devices ({', '.join(definitions)})")
        conn.execute("CREATE TABLE vehicle_profiles (id INTEGER PRIMARY KEY)")
        conn.execute("CREATE TABLE vehicle_models (id INTEGER PRIMARY KEY)")
        conn.execute(
            """
            INSERT INTO devices
                (device_code, first_seen_at, last_seen_at, vehicle_name, api_token_hash)
            VALUES (?, ?, ?, ?, ?)
            """,
            ("LC0FD4C42S7184345", "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", "宋 PLUS DM-i", "hash"),
        )

        platform_storage.remove_legacy_vehicle_configuration(conn)

        columns = {row["name"] for row in conn.execute("PRAGMA table_info(devices)")}
        device = conn.execute("SELECT * FROM devices").fetchone()
        profile_table = conn.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'vehicle_profiles'"
        ).fetchone()
        model_table = conn.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'vehicle_models'"
        ).fetchone()
        self.assertEqual(set(platform_storage.DEVICE_SCHEMA_COLUMNS), columns)
        self.assertEqual("宋 PLUS DM-i", device["vehicle_name"])
        self.assertEqual("hash", device["api_token_hash"])
        self.assertIsNone(profile_table)
        self.assertIsNone(model_table)
        conn.close()


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

    def test_miniapp_version_comparison_uses_numeric_order(self):
        self.assertTrue(platform_miniapp_api.version_is_newer("1.9.9", "1.10.0"))
        self.assertTrue(platform_miniapp_api.version_is_newer("v1.9.7", "2.0"))
        self.assertFalse(platform_miniapp_api.version_is_newer("1.10.0", "1.9.9"))
        self.assertFalse(platform_miniapp_api.version_is_newer("1.9.7", "v1.9.7.0"))
        self.assertFalse(platform_miniapp_api.version_is_newer("--", "1.9.8"))


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
                "miniapp_remote_commands",
                "vehicle_local_sounds",
                "miniapp_log_requests",
                "vehicle_trip_points",
                "vehicle_trips",
                "vehicle_charges",
                "miniapp_binding_codes",
                "miniapp_vehicle_bindings",
                "miniapp_sessions",
                "miniapp_users",
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

    def miniapp_login(self, openid="openid-test-user"):
        with patch.object(
            platform_miniapp,
            "exchange_wechat_code",
            return_value={"openid": openid, "unionid": ""},
        ):
            response = self.client.post(
                "/api/v1/miniapp/login",
                json={"code": "temporary-wechat-code"},
            )
        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json["token"])
        self.assertGreater(response.json["expiresAt"], 0)
        return response.json["token"]

    def generate_miniapp_binding_code(self, vin):
        response = self.client.post(
            "/api/v1/miniapp/vehicles/binding-code",
            json={"deviceCode": vin},
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, response.status_code)
        self.assertRegex(response.json["bindingCode"], r"^\d{8}$")
        return response.json["bindingCode"]

    def bind_miniapp_vehicle(self, miniapp_token, binding_code):
        return self.client.post(
            "/api/v1/miniapp/vehicles/bind",
            json={"bindingCode": binding_code},
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )

    def vehicle_heartbeat_payload(self, vin="LC0FD4C42S7184345"):
        return {
            "deviceCode": vin,
            "appVersion": "1.9.2",
            "packageName": "com.byd.wjx",
            "vehicle": {
                "vinYear": 2025,
                "displayName": "宋 PLUS DM-i",
                "modelVariant": "SA3HEV",
                "carSeries": "SONG_PLUS",
                "vehicleType": "PHEV",
                "controllerVersion": "57.1.3.2501010.1",
                "controllerSeries": 57,
            },
            "runtime": {
                "connected": True,
                "powerState": 1,
            },
        }

    def test_vehicle_heartbeat_records_only_real_vehicle_information(self):
        payload = self.vehicle_heartbeat_payload()
        self.register_device(payload["deviceCode"])

        response = self.client.post(
            "/api/v1/vehicles/heartbeat",
            json=payload,
            headers={"Authorization": f"Bearer {self.device_tokens[payload['deviceCode']]}"},
        )

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json["success"])
        self.assertEqual(2025, response.json["vinYear"])
        self.assertEqual("trial", response.json["authorizationStatus"])
        with platform_app.db() as conn:
            device = conn.execute(
                "SELECT * FROM devices WHERE device_code = ?",
                (payload["deviceCode"],),
            ).fetchone()
            columns = {
                row["name"] for row in conn.execute("PRAGMA table_info(devices)").fetchall()
            }
        self.assertEqual("宋 PLUS DM-i", device["vehicle_name"])
        self.assertEqual(2025, device["vin_year"])
        self.assertEqual("SA3HEV", device["model_variant"])
        self.assertEqual("SONG_PLUS", device["car_series"])
        self.assertEqual("PHEV", device["vehicle_type"])
        self.assertEqual(57, device["controller_series"])
        self.assertEqual("127.0.0.1", device["remote_addr"])
        self.assertFalse(
            {
                "sales_model",
                "compatibility_key",
                "camera_id",
                "surface_index",
                "current_profile_id",
                "profile_status",
            }
            & columns
        )

    def test_vehicle_heartbeat_requires_the_issued_device_credential(self):
        payload = self.vehicle_heartbeat_payload()

        response = self.client.post("/api/v1/vehicles/heartbeat", json=payload)

        self.assertEqual(401, response.status_code)
        self.assertFalse(response.json["success"])
        with platform_app.db() as conn:
            count = conn.execute("SELECT COUNT(*) AS c FROM devices").fetchone()["c"]
        self.assertEqual(0, count)

    def test_wrong_device_credential_cannot_overwrite_a_registered_vehicle(self):
        payload = self.vehicle_heartbeat_payload()
        vin = self.register_device(payload["deviceCode"])

        response = self.client.post(
            "/api/v1/vehicles/heartbeat",
            json=payload,
            headers={"Authorization": "Bearer wrong-device-token"},
        )

        self.assertEqual(401, response.status_code)
        with platform_app.db() as conn:
            device = conn.execute(
                "SELECT * FROM devices WHERE device_code = ?",
                (vin,),
            ).fetchone()
        self.assertEqual("", device["vehicle_name"])

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

    def test_vehicle_heartbeat_preserves_last_non_empty_identity(self):
        payload = self.vehicle_heartbeat_payload()
        vin = self.register_device(payload["deviceCode"])
        headers = {"Authorization": f"Bearer {self.device_tokens[vin]}"}
        first = self.client.post("/api/v1/vehicles/heartbeat", json=payload, headers=headers)
        self.assertEqual(200, first.status_code)

        blank = self.vehicle_heartbeat_payload(vin)
        blank["vehicle"].update({
            "displayName": "",
            "modelVariant": "",
            "carSeries": "",
            "vehicleType": "",
            "controllerVersion": "",
            "controllerSeries": 0,
        })
        second = self.client.post("/api/v1/vehicles/heartbeat", json=blank, headers=headers)

        self.assertEqual(200, second.status_code)
        with platform_app.db() as conn:
            device = conn.execute(
                "SELECT * FROM devices WHERE device_code = ?",
                (vin,),
            ).fetchone()
        self.assertEqual("宋 PLUS DM-i", device["vehicle_name"])
        self.assertEqual("SA3HEV", device["model_variant"])
        self.assertEqual(57, device["controller_series"])

    def test_vehicle_heartbeat_rejects_forged_vin_year(self):
        payload = self.vehicle_heartbeat_payload()
        payload["vehicle"]["vinYear"] = 2026

        response = self.client.post("/api/v1/vehicles/heartbeat", json=payload)

        self.assertEqual(400, response.status_code)
        self.assertFalse(response.json["success"])
        with platform_app.db() as conn:
            count = conn.execute("SELECT COUNT(*) AS c FROM devices").fetchone()["c"]
        self.assertEqual(0, count)

    def test_legacy_vehicle_profile_route_only_keeps_unupgraded_clients_online(self):
        vin = "LC0FD4C42S7184345"
        old_status = self.client.post(
            "/api/v1/license/status",
            json={"deviceCode": vin, "appVersion": "1.9.1", "packageName": "com.byd.wjx"},
        )
        self.assertEqual(200, old_status.status_code)
        self.assertNotIn("deviceToken", old_status.json)

        legacy_payload = self.vehicle_heartbeat_payload(vin)
        legacy_payload["appVersion"] = "1.9.1"
        legacy = self.client.post(
            "/api/v1/vehicle-profiles/resolve",
            json=legacy_payload,
        )
        self.assertEqual(200, legacy.status_code)
        self.assertEqual("upgrade_required", legacy.json["profileStatus"])

        upgraded = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "1.9.2",
                "packageName": "com.byd.wjx",
                "credentialVersion": 1,
            },
        )
        self.assertEqual(200, upgraded.status_code)
        self.assertTrue(upgraded.json["deviceToken"])

        rejected_legacy = self.client.post(
            "/api/v1/vehicle-profiles/resolve",
            json=legacy_payload,
        )
        self.assertEqual(401, rejected_legacy.status_code)

        self.login_and_get_csrf()
        page = self.client.get("/vehicle-profiles")
        self.assertEqual(404, page.status_code)

    def test_vehicle_pages_use_real_model_and_exact_authorization_categories(self):
        vins = (
            "LC0FD4C42S7184345",
            "LC0FD4C42S7184346",
            "LC0FD4C42S7184347",
        )
        for vin in vins:
            self.register_device(vin)
            response = self.client.post(
                "/api/v1/vehicles/heartbeat",
                json=self.vehicle_heartbeat_payload(vin),
                headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
            )
            self.assertEqual(200, response.status_code)
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
        self.assertIn("2025款 宋 PLUS DM-i".encode(), detail.data)
        self.assertIn("VIN 年份".encode(), detail.data)
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

    def test_miniapp_login_requires_wechat_configuration_without_test_exchange(self):
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("WJX_MINIAPP_APP_ID", None)
            os.environ.pop("WJX_MINIAPP_APP_SECRET", None)
            response = self.client.post(
                "/api/v1/miniapp/login",
                json={"code": "temporary-wechat-code"},
            )

        self.assertEqual(503, response.status_code)
        self.assertFalse(response.json["success"])

    def test_miniapp_binding_is_one_time_and_dashboard_uses_real_telemetry(self):
        vin = self.register_device("LC0FD4C42S7184345")
        payload = self.vehicle_heartbeat_payload(vin)
        payload["runtime"].update(
            {
                "soc": 76,
                "electricRangeKm": 118,
                "fuelRangeKm": 506,
                "gear": 1,
                "lockState": 2,
                "insideTemp": 24,
                "outsideTemp": 33,
                "mileageKm": 18426.4,
                "voltage12v": 14.1,
                "chargingStatus": 0,
            }
        )
        heartbeat = self.client.post(
            "/api/v1/vehicles/heartbeat",
            json=payload,
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, heartbeat.status_code)

        miniapp_token = self.miniapp_login()
        binding_code = self.generate_miniapp_binding_code(vin)
        bound = self.bind_miniapp_vehicle(miniapp_token, binding_code)
        self.assertEqual(200, bound.status_code)
        self.assertEqual("2025款 宋 PLUS DM-i", bound.json["vehicle"]["displayName"])

        dashboard = self.client.get(
            "/api/v1/miniapp/dashboard",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(200, dashboard.status_code)
        vehicle = dashboard.json["vehicle"]
        self.assertEqual(76, vehicle["soc"])
        self.assertEqual(624, vehicle["rangeKm"])
        self.assertEqual("P 挡", vehicle["gearLabel"])
        self.assertEqual("已锁车", vehicle["lockLabel"])
        self.assertEqual(24, vehicle["insideTemp"])
        self.assertEqual(33, vehicle["outsideTemp"])
        self.assertEqual(18426.4, vehicle["mileageKm"])
        self.assertEqual(14.1, vehicle["voltage12v"])
        self.assertNotIn(vin, vehicle["vinMasked"])

        second_user_token = self.miniapp_login("openid-second-user")
        reused = self.bind_miniapp_vehicle(second_user_token, binding_code)
        self.assertEqual(400, reused.status_code)
        self.assertEqual(401, self.client.get("/api/v1/miniapp/dashboard").status_code)

    def test_miniapp_history_sync_returns_trip_route_and_charge_records(self):
        vin = self.register_device("LC0FD4C42S7184345")
        miniapp_token = self.miniapp_login()
        binding_code = self.generate_miniapp_binding_code(vin)
        self.assertEqual(200, self.bind_miniapp_vehicle(miniapp_token, binding_code).status_code)
        start_ts = platform_miniapp.epoch_millis(platform_app.utc_now()) - 60 * 60 * 1000
        end_ts = start_ts + 32 * 60 * 1000
        sync = self.client.post(
            "/api/v1/vehicles/history-sync",
            json={
                "deviceCode": vin,
                "trips": [
                    {
                        "sourceId": "trip-41",
                        "startTs": start_ts,
                        "endTs": end_ts,
                        "distanceKm": 18.4,
                        "kwhConsumed": 2.54,
                        "kwhPer100km": 13.8,
                        "avgSpeedKmh": 34.5,
                        "socStart": 82,
                        "socEnd": 76,
                        "cost": 1.45,
                    }
                ],
                "tripPoints": [
                    {
                        "sourceId": "point-1",
                        "tripSourceId": "trip-41",
                        "timestamp": start_ts,
                        "lat": 30.641,
                        "lon": 104.043,
                        "speedKmh": 0,
                    },
                    {
                        "sourceId": "point-2",
                        "tripSourceId": "trip-41",
                        "timestamp": end_ts,
                        "lat": 30.677,
                        "lon": 104.092,
                        "speedKmh": 0,
                    },
                ],
                "charges": [
                    {
                        "sourceId": "charge-12",
                        "startTs": start_ts - 24 * 60 * 60 * 1000,
                        "endTs": start_ts - 24 * 60 * 60 * 1000 + 46 * 60 * 1000,
                        "socStart": 28,
                        "socEnd": 82,
                        "kwhCharged": 31.8,
                        "type": "DC",
                        "cost": 38.4,
                        "status": "COMPLETED",
                    }
                ],
            },
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, sync.status_code)
        self.assertEqual(1, sync.json["trips"])
        self.assertEqual(2, sync.json["tripPoints"])
        self.assertEqual(1, sync.json["charges"])

        listing = self.client.get(
            "/api/v1/miniapp/trips",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(200, listing.status_code)
        self.assertEqual(1, listing.json["summary"]["tripCount"])
        self.assertEqual(18.4, listing.json["summary"]["distanceKm"])
        self.assertEqual(2, len(listing.json["latestRoute"]["markers"]))
        self.assertEqual("直流快充", listing.json["charges"][0]["type"])

        trip_id = listing.json["items"][0]["id"]
        detail = self.client.get(
            f"/api/v1/miniapp/trips/{trip_id}",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(200, detail.status_code)
        self.assertEqual(2, len(detail.json["route"]["polyline"][0]["points"]))

        unauthorized_sync = self.client.post(
            "/api/v1/vehicles/history-sync",
            json={"deviceCode": vin},
        )
        self.assertEqual(401, unauthorized_sync.status_code)

    def test_miniapp_log_request_is_completed_by_the_next_vehicle_upload(self):
        vin = self.register_device("LC0FD4C42S7184345")
        miniapp_token = self.miniapp_login()
        binding_code = self.generate_miniapp_binding_code(vin)
        self.assertEqual(200, self.bind_miniapp_vehicle(miniapp_token, binding_code).status_code)

        requested = self.client.post(
            "/api/v1/miniapp/logs/request-upload",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(200, requested.status_code)
        request_id = requested.json["requestId"]

        heartbeat = self.client.post(
            "/api/v1/vehicles/heartbeat",
            json=self.vehicle_heartbeat_payload(vin),
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(request_id, heartbeat.json["logUploadRequest"]["id"])

        uploaded = self.upload_log(vin)
        self.assertEqual(200, uploaded.status_code)
        with platform_app.db() as conn:
            log_request = conn.execute(
                "SELECT * FROM miniapp_log_requests WHERE id = ?",
                (request_id,),
            ).fetchone()
        self.assertEqual("completed", log_request["status"])
        self.assertEqual(uploaded.json["logId"], log_request["diagnostic_log_id"])

        profile = self.client.get(
            "/api/v1/miniapp/profile",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual("最近上传成功", profile.json["diagnostic"]["lastUploadLabel"])

    def test_remote_audio_command_requires_binding_and_real_vehicle_receipt(self):
        vin = self.register_device("LC0FD4C42S7184345")
        miniapp_token = self.miniapp_login()
        sound_key = "a" * 64
        unbound = self.client.post(
            "/api/v1/miniapp/remote-audio/play",
            json={"soundKey": sound_key},
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(400, unbound.status_code)

        binding_code = self.generate_miniapp_binding_code(vin)
        self.assertEqual(200, self.bind_miniapp_vehicle(miniapp_token, binding_code).status_code)

        synced = self.client.post(
            "/api/v1/vehicles/commands/poll",
            json={
                "deviceCode": vin,
                "localSounds": [
                    {
                        "soundKey": sound_key,
                        "fileName": "远程欢迎音.wav",
                        "displayName": "远程欢迎音",
                        "fileSize": 4096,
                        "modifiedAt": 1_721_234_567_000,
                    }
                ],
            },
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, synced.status_code)
        self.assertIsNone(synced.json["command"])

        listing = self.client.get(
            "/api/v1/miniapp/remote-audio",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(200, listing.status_code)
        self.assertEqual(sound_key, listing.json["sounds"][0]["id"])
        self.assertEqual("远程欢迎音.wav", listing.json["sounds"][0]["fileName"])

        queued = self.client.post(
            "/api/v1/miniapp/remote-audio/play",
            json={"soundKey": sound_key},
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(200, queued.status_code)
        command_id = queued.json["command"]["id"]
        self.assertEqual("pending", queued.json["command"]["status"])

        unauthorized_poll = self.client.post(
            "/api/v1/vehicles/commands/poll",
            json={"deviceCode": vin},
        )
        self.assertEqual(401, unauthorized_poll.status_code)

        polled = self.client.post(
            "/api/v1/vehicles/commands/poll",
            json={"deviceCode": vin},
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, polled.status_code)
        self.assertEqual(command_id, polled.json["command"]["id"])
        self.assertEqual("delivered", polled.json["command"]["status"])
        self.assertEqual(sound_key, polled.json["command"]["payload"]["soundKey"])

        before_receipt = self.client.get(
            f"/api/v1/miniapp/remote-audio/commands/{command_id}",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual("delivered", before_receipt.json["command"]["status"])

        completed = self.client.post(
            f"/api/v1/vehicles/commands/{command_id}/complete",
            json={
                "deviceCode": vin,
                "status": "completed",
                "message": "已开始车外播放",
                "result": {"soundKey": sound_key},
            },
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, completed.status_code)
        self.assertEqual("completed", completed.json["command"]["status"])

        final = self.client.get(
            f"/api/v1/miniapp/remote-audio/commands/{command_id}",
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual("completed", final.json["command"]["status"])
        self.assertEqual("已开始车外播放", final.json["command"]["message"])

        cleared = self.client.post(
            "/api/v1/vehicles/commands/poll",
            json={"deviceCode": vin, "localSounds": []},
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, cleared.status_code)
        missing = self.client.post(
            "/api/v1/miniapp/remote-audio/play",
            json={"soundKey": sound_key},
            headers={"Authorization": f"Bearer {miniapp_token}"},
        )
        self.assertEqual(404, missing.status_code)

    def test_remote_audio_expired_command_is_not_delivered(self):
        vin = self.register_device("LC0FD4C42S7184345")
        miniapp_token = self.miniapp_login()
        binding_code = self.generate_miniapp_binding_code(vin)
        self.assertEqual(200, self.bind_miniapp_vehicle(miniapp_token, binding_code).status_code)
        with platform_app.db() as conn:
            user_id = conn.execute("SELECT id FROM miniapp_users LIMIT 1").fetchone()["id"]
            device_id = conn.execute("SELECT id FROM devices WHERE device_code = ?", (vin,)).fetchone()["id"]
            cursor = conn.execute(
                """
                INSERT INTO miniapp_remote_commands
                    (device_id, requested_by_user_id, command_type, payload_json,
                     status, message, requested_at, expires_at)
                VALUES (?, ?, 'stop_outside_sound', '{}', 'pending', '', ?, ?)
                """,
                (device_id, user_id, "2020-01-01T00:00:00Z", "2020-01-01T00:01:00Z"),
            )
            command_id = cursor.lastrowid

        polled = self.client.post(
            "/api/v1/vehicles/commands/poll",
            json={"deviceCode": vin},
            headers={"Authorization": f"Bearer {self.device_tokens[vin]}"},
        )
        self.assertEqual(200, polled.status_code)
        self.assertIsNone(polled.json["command"])
        with platform_app.db() as conn:
            status = conn.execute(
                "SELECT status FROM miniapp_remote_commands WHERE id = ?",
                (command_id,),
            ).fetchone()["status"]
        self.assertEqual("expired", status)


if __name__ == "__main__":
    unittest.main()
