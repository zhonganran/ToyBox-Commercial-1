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
                "vehicle_profiles",
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

    def register_device(self, vin="L1234567890123456"):
        response = self.client.post(
            "/api/v1/license/status",
            json={
                "deviceCode": vin,
                "appVersion": "1.9",
                "packageName": "com.byd.wjx",
            },
        )
        self.assertEqual(200, response.status_code)
        return vin

    def upload_log(self, vin, data=b"07-23 12:00:00.000 I/Test diagnostic\n", filename="wjx.log"):
        return self.client.post(
            "/api/v1/logs/upload",
            data={
                "deviceCode": vin,
                "appVersion": "1.9",
                "packageName": "com.byd.wjx",
                "log": (io.BytesIO(data), filename, "text/plain"),
            },
            content_type="multipart/form-data",
        )

    def profile_payload(self, vin="L1234567890123456", manual_override=False):
        vehicle = {
            "displayName": "宋 PLUS DM-i",
            "modelVariant": "SA3HEV",
            "vehicle40dCode": "BYD-S3",
            "carSeries": "SONG_PLUS",
            "vehicleType": "PHEV",
            "controllerVersion": "57.1.3.2501010.1",
            "controllerCompat": "57.1.3",
            "controllerSeries": 57,
            "cameraSourceFingerprint": "a" * 64,
        }
        vehicle["compatibilityKey"] = platform_app.vehicle_compatibility_key(vehicle)
        return {
            "deviceCode": vin,
            "appVersion": "1.9.1",
            "packageName": "com.byd.wjx",
            "vehicle": vehicle,
            "runtime": {
                "connected": True,
                "powerState": 1,
                "runtimeState": "active",
                "manualOverride": manual_override,
                "currentProfileId": "",
                "currentProfileVersion": 0,
                "cameraId": 2,
                "surfaceIndex": 0,
                "leftSource": "SOURCE_A",
                "rightSource": "SOURCE_B",
                "rearSource": "SOURCE_C",
            },
        }

    def insert_vehicle_profile(self, payload, status="verified", sales_model="2026款"):
        vehicle = payload["vehicle"]
        now = platform_app.utc_now()
        with platform_app.db() as conn:
            conn.execute(
                """
                INSERT INTO vehicle_profiles
                    (profile_id, profile_version, status, compatibility_key, vehicle_name,
                     sales_model, model_variant, vehicle_40d_code, car_series, vehicle_type,
                     controller_series, controller_compat, camera_source_fingerprint,
                     camera_id, surface_index, left_source, right_source, rear_source,
                     note, created_at, updated_at)
                VALUES (?, 3, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 2, 0,
                        'SOURCE_A', 'SOURCE_B', 'SOURCE_C', '', ?, ?)
                """,
                (
                    "mirror-song-plus-57",
                    status,
                    vehicle["compatibilityKey"],
                    vehicle["displayName"],
                    sales_model,
                    vehicle["modelVariant"],
                    vehicle["vehicle40dCode"],
                    vehicle["carSeries"],
                    vehicle["vehicleType"],
                    vehicle["controllerSeries"],
                    vehicle["controllerCompat"],
                    vehicle["cameraSourceFingerprint"],
                    now,
                    now,
                ),
            )

    def test_vehicle_profile_rejects_client_forged_compatibility_key(self):
        payload = self.profile_payload()
        payload["vehicle"]["compatibilityKey"] = "0" * 64

        response = self.client.post("/api/v1/vehicle-profiles/resolve", json=payload)

        self.assertEqual(400, response.status_code)
        self.assertFalse(response.json["success"])
        with platform_app.db() as conn:
            self.assertEqual(0, conn.execute("SELECT COUNT(*) AS c FROM devices").fetchone()["c"])

    def test_vehicle_profile_draft_is_recorded_but_never_returned(self):
        payload = self.profile_payload()
        self.insert_vehicle_profile(payload, status="draft")

        response = self.client.post("/api/v1/vehicle-profiles/resolve", json=payload)

        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json["success"])
        self.assertEqual("pending", response.json["profileStatus"])
        self.assertIsNone(response.json["profile"])
        with platform_app.db() as conn:
            device = conn.execute("SELECT * FROM devices WHERE device_code = ?", (payload["deviceCode"],)).fetchone()
        self.assertEqual(payload["vehicle"]["compatibilityKey"], device["compatibility_key"])
        self.assertEqual(57, device["controller_series"])
        self.assertEqual("127.0.0.1", device["remote_addr"])

    def test_verified_exact_profile_is_returned_even_when_client_has_manual_override(self):
        payload = self.profile_payload(manual_override=True)
        self.insert_vehicle_profile(payload)

        response = self.client.post("/api/v1/vehicle-profiles/resolve", json=payload)

        self.assertEqual(200, response.status_code)
        self.assertEqual("matched", response.json["profileStatus"])
        self.assertEqual("2026款", response.json["salesModel"])
        self.assertEqual("mirror-song-plus-57", response.json["profile"]["profileId"])
        self.assertEqual(3, response.json["profile"]["version"])
        self.assertEqual(2, response.json["profile"]["cameraId"])
        with platform_app.db() as conn:
            device = conn.execute("SELECT * FROM devices WHERE device_code = ?", (payload["deviceCode"],)).fetchone()
        self.assertEqual("2026款", device["sales_model"])
        self.assertEqual(1, device["manual_override"])
        self.assertEqual("matched", device["profile_status"])

    def test_expired_unlicensed_vehicle_does_not_receive_verified_profile(self):
        payload = self.profile_payload()
        self.insert_vehicle_profile(payload)
        first = self.client.post("/api/v1/vehicle-profiles/resolve", json=payload)
        self.assertEqual("matched", first.json["profileStatus"])
        with platform_app.db() as conn:
            conn.execute(
                """
                UPDATE devices SET activated = 0, trial_started_at = '2020-01-01T00:00:00Z',
                    trial_ends_at = '2020-01-08T00:00:00Z'
                WHERE device_code = ?
                """,
                (payload["deviceCode"],),
            )

        response = self.client.post("/api/v1/vehicle-profiles/resolve", json=payload)

        self.assertEqual(200, response.status_code)
        self.assertEqual("unauthorized", response.json["profileStatus"])
        self.assertIsNone(response.json["profile"])

    def test_admin_can_review_vehicle_create_draft_and_verify_profile(self):
        payload = self.profile_payload()
        pending = self.client.post("/api/v1/vehicle-profiles/resolve", json=payload)
        self.assertEqual("pending", pending.json["profileStatus"])
        with platform_app.db() as conn:
            device_id = conn.execute(
                "SELECT id FROM devices WHERE device_code = ?", (payload["deviceCode"],)
            ).fetchone()["id"]
        csrf_token = self.login_and_get_csrf()

        listing = self.client.get("/vehicles")
        detail = self.client.get(f"/vehicles/{device_id}")
        create_page = self.client.get(f"/vehicle-profiles?device={device_id}")
        self.assertEqual(200, listing.status_code)
        self.assertEqual(200, detail.status_code)
        self.assertEqual(200, create_page.status_code)
        self.assertIn("宋 PLUS DM-i".encode(), create_page.data)

        created = self.client.post(
            "/vehicle-profiles",
            data={
                "csrf_token": csrf_token,
                "device_id": str(device_id),
                "vehicle_name": "宋 PLUS DM-i",
                "sales_model": "2026款",
                "profile_version": "1",
                "camera_id": "2",
                "surface_index": "0",
                "left_source": "SOURCE_A",
                "right_source": "SOURCE_B",
                "rear_source": "SOURCE_C",
                "note": "用户已确认画面正常",
            },
            follow_redirects=False,
        )
        self.assertEqual(302, created.status_code)
        with platform_app.db() as conn:
            profile = conn.execute("SELECT * FROM vehicle_profiles").fetchone()
        self.assertEqual("draft", profile["status"])

        verified = self.client.post(
            f"/vehicle-profiles/{profile['id']}/toggle",
            data={"csrf_token": csrf_token},
            follow_redirects=False,
        )
        self.assertEqual(302, verified.status_code)
        with platform_app.db() as conn:
            status = conn.execute(
                "SELECT status FROM vehicle_profiles WHERE id = ?", (profile["id"],)
            ).fetchone()["status"]
        self.assertEqual("verified", status)

        matched = self.client.post("/api/v1/vehicle-profiles/resolve", json=payload)
        self.assertEqual("matched", matched.json["profileStatus"])
        self.assertEqual("2026款", matched.json["salesModel"])

    def test_diagnostic_log_upload_requires_registered_vehicle(self):
        response = self.upload_log("L1234567890123456")

        self.assertEqual(403, response.status_code)
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

    def test_diagnostic_log_rejects_wrong_extension_binary_and_oversize(self):
        vin = self.register_device()

        wrong_extension = self.upload_log(vin, b"plain text", "payload.apk")
        self.assertEqual(400, wrong_extension.status_code)

        binary = self.upload_log(vin, b"text\x00binary", "wjx.log")
        self.assertEqual(400, binary.status_code)

        with patch.object(platform_app, "MAX_LOG_SIZE", 4):
            oversize = self.upload_log(vin, b"12345", "wjx.log")
        self.assertEqual(413, oversize.status_code)
        self.assertEqual([], list(platform_app.LOG_DIR.iterdir()))


if __name__ == "__main__":
    unittest.main()
