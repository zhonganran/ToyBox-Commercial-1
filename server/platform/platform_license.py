import base64
import logging
import secrets
import time
from datetime import datetime, timedelta
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

from platform_config import (
    LICENSE_ALPHABET,
    OFFLINE_LICENSE_FEATURES,
    OFFLINE_LICENSE_PRIVATE_KEY_FILE,
    OFFLINE_LICENSE_PRIVATE_KEY_PEM,
)
from platform_identity import extract_valid_vin, normalize_device_code
from platform_storage import utc_now


LOGGER = logging.getLogger(__name__)


def normalize_license_code(value):
    normalized = "".join(character for character in (value or "").upper() if character.isalnum())
    return normalized[3:] if normalized.startswith("STB") else normalized


def add_days_iso(days):
    return (datetime.utcnow() + timedelta(days=max(0, days))).replace(microsecond=0).isoformat() + "Z"


def add_days_from_iso(value, days):
    parsed = parse_utc(value)
    if parsed is None:
        return add_days_iso(days)
    return (parsed + timedelta(days=max(0, days))).replace(microsecond=0).isoformat() + "Z"


def parse_utc(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).replace(tzinfo=None)
    except ValueError:
        return None


def epoch_millis(value):
    parsed = parse_utc(value)
    return int(parsed.timestamp() * 1000) if parsed is not None else 0


def format_license_code(raw):
    normalized = normalize_license_code(raw)
    if not normalized:
        return ""
    return "STB-" + "-".join(normalized[index:index + 4] for index in range(0, len(normalized), 4))


def is_valid_license_code(code):
    raw = normalize_license_code(code)
    return len(raw) == 20 and all(character in LICENSE_ALPHABET for character in raw)


def generate_license_code():
    raw = "".join(secrets.choice(LICENSE_ALPHABET) for _ in range(20))
    return format_license_code(raw)


def mask_license_code(code):
    formatted = format_license_code(code)
    parts = formatted.split("-")
    if len(parts) >= 6 and parts[0] == "STB":
        return f"STB-{parts[1]}-****-****-****-{parts[-1]}"
    raw = normalize_license_code(code)
    if len(raw) <= 8:
        return raw
    return f"{raw[:4]}-****-****-{raw[-4:]}"


def offline_license_canonical(payload):
    device_codes = ",".join(
        sorted(
            {
                normalize_device_code(code)
                for code in payload.get("deviceCodes", [])
                if normalize_device_code(code)
            }
        )
    )
    features = ",".join(sorted({str(feature) for feature in payload.get("features", []) if str(feature)}))
    fields = [
        f"version={int(payload.get('version') or 1)}",
        f"signatureAlg={payload.get('signatureAlg') or 'RS256'}",
        f"deviceCodes={device_codes}",
        f"activated={'1' if payload.get('activated') else '0'}",
        f"activatedAt={int(payload.get('activatedAt') or 0)}",
        f"issuedAt={int(payload.get('issuedAt') or 0)}",
        f"expiresAt={int(payload.get('expiresAt') or 0)}",
        f"packageName={payload.get('packageName') or ''}",
        f"licenseCodeMasked={payload.get('licenseCodeMasked') or ''}",
        f"features={features}",
    ]
    return "\n".join(fields)


def offline_license_private_key_material():
    pem = OFFLINE_LICENSE_PRIVATE_KEY_PEM.strip().replace("\\n", "\n")
    if pem:
        return pem, "环境变量"
    key_path = Path(OFFLINE_LICENSE_PRIVATE_KEY_FILE)
    if key_path.is_file():
        return key_path.read_text(encoding="utf-8").strip(), str(key_path)
    return "", str(key_path)


def load_offline_license_private_key():
    pem, _source = offline_license_private_key_material()
    if not pem:
        LOGGER.warning("offline license private key is not configured")
        return None
    try:
        return serialization.load_pem_private_key(pem.encode("utf-8"), password=None)
    except Exception as exc:
        LOGGER.warning("offline license private key load failed: %s", exc)
        return None


def offline_license_private_key_status():
    pem, source = offline_license_private_key_material()
    if not pem:
        return False, f"未配置：{source}"
    try:
        serialization.load_pem_private_key(pem.encode("utf-8"), password=None)
        return True, source
    except Exception as exc:
        return False, f"加载失败：{exc}"


def sign_offline_license(payload):
    private_key = load_offline_license_private_key()
    if private_key is None:
        return ""
    signature = private_key.sign(
        offline_license_canonical(payload).encode("utf-8"),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )
    return base64.urlsafe_b64encode(signature).decode("ascii").rstrip("=")


def verify_offline_license_proof(proof, device_codes, package_name):
    if not isinstance(proof, dict) or not proof.get("activated"):
        return False
    proof_codes = {
        normalize_device_code(code)
        for code in proof.get("deviceCodes", [])
        if extract_valid_vin(code)
    }
    requested_codes = {normalize_device_code(code) for code in device_codes if extract_valid_vin(code)}
    if not proof_codes.intersection(requested_codes):
        return False
    proof_package = str(proof.get("packageName") or "")
    if proof_package and proof_package != str(package_name or ""):
        return False
    expires_at = _bounded_int(proof.get("expiresAt"), default=0, minimum=0, maximum=9_223_372_036_854_775_807)
    if expires_at > 0 and expires_at <= int(time.time() * 1000):
        return False
    signature = str(proof.get("signature") or "").strip()
    if proof.get("signatureAlg") != "RS256" or not signature:
        return False
    private_key = load_offline_license_private_key()
    if private_key is None:
        return False
    try:
        padded = signature + "=" * ((4 - len(signature) % 4) % 4)
        private_key.public_key().verify(
            base64.urlsafe_b64decode(padded.encode("ascii")),
            offline_license_canonical(proof).encode("utf-8"),
            padding.PKCS1v15(),
            hashes.SHA256(),
        )
        return True
    except Exception:
        return False


def build_offline_license(device_codes, package_name, license_code, activated_at):
    codes = sorted({normalize_device_code(code) for code in device_codes if extract_valid_vin(code)})
    if not codes:
        return None
    activated_at_ms = int(activated_at or 0)
    if activated_at_ms <= 0:
        activated_at_ms = int(time.time() * 1000)
    payload = {
        "version": 1,
        "signatureAlg": "RS256",
        "deviceCodes": codes,
        "activated": True,
        "activatedAt": activated_at_ms,
        "issuedAt": int(time.time() * 1000),
        "expiresAt": 0,
        "packageName": package_name or "",
        "licenseCodeMasked": mask_license_code(license_code),
        "features": OFFLINE_LICENSE_FEATURES,
    }
    signature = sign_offline_license(payload)
    if not signature:
        return None
    payload["signature"] = signature
    return payload


def license_row_activated_at(conn, row):
    if row is None:
        return "", 0
    for key in ("activated_at", "updated_at", "created_at"):
        value = row[key] or ""
        millis = epoch_millis(value)
        if millis > 0:
            if key != "activated_at":
                conn.execute(
                    """
                    UPDATE license_codes
                    SET activated_at = ?, updated_at = ?
                    WHERE id = ? AND (activated_at IS NULL OR activated_at = '')
                    """,
                    (value, utc_now(), row["id"]),
                )
                LOGGER.info("backfilled license activated_at id=%s source=%s", row["id"], key)
            return value, millis
    now = utc_now()
    conn.execute(
        "UPDATE license_codes SET activated_at = ?, updated_at = ? WHERE id = ?",
        (now, now, row["id"]),
    )
    LOGGER.info("backfilled license activated_at id=%s source=now", row["id"])
    return now, epoch_millis(now)


def _bounded_int(value, default, minimum, maximum):
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return min(maximum, max(minimum, parsed))
