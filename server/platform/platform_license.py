import logging
import secrets
from datetime import datetime, timedelta

from platform_config import LICENSE_ALPHABET
from platform_storage import utc_datetime, utc_now


LOGGER = logging.getLogger(__name__)


def normalize_license_code(value):
    normalized = "".join(character for character in (value or "").upper() if character.isalnum())
    return normalized[3:] if normalized.startswith("STB") else normalized


def add_days_iso(days):
    return (utc_datetime() + timedelta(days=max(0, days))).replace(microsecond=0).isoformat() + "Z"


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
