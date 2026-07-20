import base64
import io
import os
import tempfile
import unittest
import wave
from unittest.mock import patch
from urllib.parse import urlsplit

_TEST_DATA_DIR = tempfile.TemporaryDirectory()
os.environ["WJX_PLATFORM_DATA_DIR"] = _TEST_DATA_DIR.name
os.environ["WJX_PLATFORM_ADMIN_USER"] = "admin"
os.environ["WJX_PLATFORM_ADMIN_PASSWORD"] = "test-admin-password"
os.environ["WJX_PLATFORM_SECRET_KEY"] = "test-secret-key"
os.environ["WJX_PLATFORM_TRUSTED_PROXY_COUNT"] = "0"

import app as platform_app


class AdminBootstrapSecurityTest(unittest.TestCase):
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


class PlatformRequestSecurityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        platform_app.app.config.update(TESTING=True)
        platform_app.init_db(force=True)

    def setUp(self):
        self.client = platform_app.app.test_client()

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
                "sounds",
                "license_codes",
                "devices",
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
            json={"deviceCode": vin, "appVersion": "test", "packageName": "com.example.test"},
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

        activated_status = self.client.post(
            "/api/v1/license/status",
            json={"deviceCode": vin, "appVersion": "test", "packageName": "com.example.test"},
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


if __name__ == "__main__":
    unittest.main()
