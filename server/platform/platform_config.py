import os
from pathlib import Path
from urllib.parse import unquote, urlsplit, urlunsplit


def validated_https_base_url(name, raw_value, *, allow_path):
    value = str(raw_value or "").strip()
    try:
        parsed = urlsplit(value)
        hostname = parsed.hostname
        parsed.port
    except ValueError as exc:
        raise RuntimeError(f"{name} must be a valid HTTPS URL") from exc
    decoded_path = unquote(parsed.path or "")
    path_segments = decoded_path.split("/")
    if (
        parsed.scheme.lower() != "https"
        or not hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or "\\" in value
        or any(ord(char) < 32 for char in value)
        or any(segment in {".", ".."} for segment in path_segments)
        or (not allow_path and decoded_path not in {"", "/"})
    ):
        raise RuntimeError(f"{name} must be credential-free HTTPS without URL pollution")
    normalized_path = parsed.path.rstrip("/") if allow_path else ""
    return urlunsplit(("https", parsed.netloc, normalized_path, "", ""))


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = Path(os.environ.get("WJX_PLATFORM_DATA_DIR", "/var/lib/supertoybox/platform"))
UPLOAD_DIR = Path(os.environ.get("WJX_PLATFORM_UPLOAD_DIR", DATA_DIR / "uploads"))
APK_DIR = Path(os.environ.get("WJX_PLATFORM_APK_DIR", UPLOAD_DIR / "apk"))
SERVICE_QR_DIR = Path(os.environ.get("WJX_PLATFORM_SERVICE_QR_DIR", UPLOAD_DIR / "service_qrcodes"))
LOG_DIR = Path(os.environ.get("WJX_PLATFORM_LOG_DIR", UPLOAD_DIR / "diagnostic_logs"))
DB_PATH = Path(os.environ.get("WJX_PLATFORM_DB", DATA_DIR / "supertoybox.db"))
PUBLIC_BASE_URL = validated_https_base_url(
    "WJX_PLATFORM_PUBLIC_BASE_URL",
    os.environ.get("WJX_PLATFORM_PUBLIC_BASE_URL", "https://wjx.689588.xyz"),
    allow_path=False,
)
PAYMENT_GATEWAY_BASE_URL = validated_https_base_url(
    "WJX_PAYMENT_GATEWAY_BASE_URL",
    os.environ.get("WJX_PAYMENT_GATEWAY_BASE_URL", "https://mzf.mapay.cc/xpay/epayn"),
    allow_path=True,
)
PAYMENT_PID = os.environ.get("WJX_PAYMENT_PID", "").strip()
PAYMENT_MERCHANT_PRIVATE_KEY_PEM = os.environ.get(
    "WJX_PAYMENT_MERCHANT_PRIVATE_KEY_PEM",
    "",
).strip()
PAYMENT_MERCHANT_PRIVATE_KEY_FILE = os.environ.get(
    "WJX_PAYMENT_MERCHANT_PRIVATE_KEY_FILE",
    str(DATA_DIR / "payment_merchant_private.pem"),
).strip()
PAYMENT_PLATFORM_PUBLIC_KEY_PEM = os.environ.get(
    "WJX_PAYMENT_PLATFORM_PUBLIC_KEY_PEM",
    "",
).strip()
PAYMENT_PLATFORM_PUBLIC_KEY_FILE = os.environ.get(
    "WJX_PAYMENT_PLATFORM_PUBLIC_KEY_FILE",
    str(DATA_DIR / "payment_platform_public.pem"),
).strip()
PAYMENT_ACTIVATION_PRICE = os.environ.get("WJX_PAYMENT_ACTIVATION_PRICE", "15.90").strip()
PAYMENT_SITE_NAME = os.environ.get("WJX_PAYMENT_SITE_NAME", "超级玩具箱").strip() or "超级玩具箱"
PAYMENT_ORDER_TTL_SECONDS = max(
    300,
    min(int(os.environ.get("WJX_PAYMENT_ORDER_TTL_SECONDS", "300")), 86400),
)
PAYMENT_HTTP_TIMEOUT_SECONDS = max(
    3,
    min(int(os.environ.get("WJX_PAYMENT_HTTP_TIMEOUT_SECONDS", "10")), 30),
)
PAYMENT_QUERY_INTERVAL_SECONDS = max(
    2,
    min(int(os.environ.get("WJX_PAYMENT_QUERY_INTERVAL_SECONDS", "4")), 30),
)
PAYMENT_CREATE_DEVICE_LIMIT = max(
    1,
    min(int(os.environ.get("WJX_PAYMENT_CREATE_DEVICE_LIMIT", "6")), 100),
)
PAYMENT_CREATE_IP_LIMIT = max(
    1,
    min(int(os.environ.get("WJX_PAYMENT_CREATE_IP_LIMIT", "60")), 1000),
)
PAYMENT_CREATE_WINDOW_SECONDS = max(
    60,
    min(int(os.environ.get("WJX_PAYMENT_CREATE_WINDOW_SECONDS", "3600")), 86400),
)
ADMIN_TITLE = os.environ.get("WJX_PLATFORM_ADMIN_TITLE", "超级玩具箱商业平台")
MAX_APK_SIZE = int(os.environ.get("WJX_PLATFORM_MAX_APK_SIZE", str(240 * 1024 * 1024)))
MAX_SERVICE_QR_SIZE = int(os.environ.get("WJX_PLATFORM_MAX_SERVICE_QR_SIZE", str(8 * 1024 * 1024)))
MAX_LOG_SIZE = int(os.environ.get("WJX_PLATFORM_MAX_LOG_SIZE", str(8 * 1024 * 1024)))
MAX_UPLOAD_SIZE = max(MAX_APK_SIZE, MAX_SERVICE_QR_SIZE, MAX_LOG_SIZE)
PASSWORD_HASH_METHOD = "pbkdf2:sha256"
INSECURE_ADMIN_PASSWORD = "admin123456"
MIN_ADMIN_PASSWORD_LENGTH = 8
ALLOWED_IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "webp"}
ALLOWED_LOG_EXTENSIONS = {"log", "txt"}
LOG_UPLOAD_DEVICE_LIMIT = int(os.environ.get("WJX_PLATFORM_LOG_UPLOAD_DEVICE_LIMIT", "6"))
LOG_UPLOAD_IP_LIMIT = int(os.environ.get("WJX_PLATFORM_LOG_UPLOAD_IP_LIMIT", "20"))
LICENSE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
DEFAULT_TRIAL_DAYS = int(os.environ.get("WJX_PLATFORM_TRIAL_DAYS", "7"))
LOGIN_MAX_FAILURES = int(os.environ.get("WJX_PLATFORM_LOGIN_MAX_FAILURES", "5"))
LOGIN_LOCK_SECONDS = int(os.environ.get("WJX_PLATFORM_LOGIN_LOCK_SECONDS", "600"))
LICENSE_MAX_FAILURES = int(os.environ.get("WJX_PLATFORM_LICENSE_MAX_FAILURES", "8"))
LICENSE_LOCK_SECONDS = int(os.environ.get("WJX_PLATFORM_LICENSE_LOCK_SECONDS", "900"))
TRUSTED_PROXY_COUNT = max(
    0,
    min(int(os.environ.get("WJX_PLATFORM_TRUSTED_PROXY_COUNT", "0")), 8),
)
DEFAULT_CONFIG = {
    "license_trial_days": str(DEFAULT_TRIAL_DAYS),
    "update_enabled": "1",
}
SERVICE_QR_TYPES = {
    "wechat": ("微信客服", "扫码添加客服", "客服二维码变更时，只需要在后台重新上传"),
    "wechat_group": ("微信交流群", "扫码加入交流群", "微信群二维码过期后重新上传，客户端会自动显示最新图"),
}
