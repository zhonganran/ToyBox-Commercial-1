import os
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = Path(os.environ.get("WJX_PLATFORM_DATA_DIR", "/var/lib/supertoybox/platform"))
UPLOAD_DIR = Path(os.environ.get("WJX_PLATFORM_UPLOAD_DIR", DATA_DIR / "uploads"))
APK_DIR = Path(os.environ.get("WJX_PLATFORM_APK_DIR", UPLOAD_DIR / "apk"))
SOUND_DIR = Path(os.environ.get("WJX_PLATFORM_SOUND_DIR", UPLOAD_DIR / "sounds"))
SERVICE_QR_DIR = Path(os.environ.get("WJX_PLATFORM_SERVICE_QR_DIR", UPLOAD_DIR / "service_qrcodes"))
LOG_DIR = Path(os.environ.get("WJX_PLATFORM_LOG_DIR", UPLOAD_DIR / "diagnostic_logs"))
DB_PATH = Path(os.environ.get("WJX_PLATFORM_DB", DATA_DIR / "supertoybox.db"))
PUBLIC_API_BASE_URL = os.environ.get(
    "WJX_PLATFORM_PUBLIC_API_BASE_URL",
    "https://api.689588.xyz",
).rstrip("/")
PUBLIC_CDN_BASE_URL = os.environ.get(
    "WJX_PLATFORM_PUBLIC_CDN_BASE_URL",
    "https://cdn.689588.xyz",
).rstrip("/")
PAYMENT_GATEWAY_BASE_URL = os.environ.get(
    "WJX_PAYMENT_GATEWAY_BASE_URL",
    "https://mzf.mapay.cc/xpay/epayn",
).rstrip("/")
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
    min(int(os.environ.get("WJX_PAYMENT_ORDER_TTL_SECONDS", "1800")), 86400),
)
PAYMENT_HTTP_TIMEOUT_SECONDS = max(
    3,
    min(int(os.environ.get("WJX_PAYMENT_HTTP_TIMEOUT_SECONDS", "10")), 30),
)
PAYMENT_QUERY_INTERVAL_SECONDS = max(
    2,
    min(int(os.environ.get("WJX_PAYMENT_QUERY_INTERVAL_SECONDS", "4")), 30),
)
ADMIN_TITLE = os.environ.get("WJX_PLATFORM_ADMIN_TITLE", "超级玩具箱商业平台")
MAX_APK_SIZE = int(os.environ.get("WJX_PLATFORM_MAX_APK_SIZE", str(240 * 1024 * 1024)))
MAX_SOUND_SIZE = int(os.environ.get("WJX_PLATFORM_MAX_SOUND_SIZE", str(80 * 1024 * 1024)))
MAX_SERVICE_QR_SIZE = int(os.environ.get("WJX_PLATFORM_MAX_SERVICE_QR_SIZE", str(8 * 1024 * 1024)))
MAX_LOG_SIZE = int(os.environ.get("WJX_PLATFORM_MAX_LOG_SIZE", str(8 * 1024 * 1024)))
MAX_UPLOAD_SIZE = max(MAX_APK_SIZE, MAX_SOUND_SIZE, MAX_SERVICE_QR_SIZE, MAX_LOG_SIZE)
PASSWORD_HASH_METHOD = "pbkdf2:sha256"
INSECURE_ADMIN_PASSWORD = "admin123456"
MIN_ADMIN_PASSWORD_LENGTH = 8
ALLOWED_SOUND_EXTENSIONS = {"mp3", "wav", "m4a", "aac", "ogg", "flac"}
ALLOWED_IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "webp"}
ALLOWED_LOG_EXTENSIONS = {"log", "txt"}
LOG_UPLOAD_DEVICE_LIMIT = int(os.environ.get("WJX_PLATFORM_LOG_UPLOAD_DEVICE_LIMIT", "6"))
LOG_UPLOAD_IP_LIMIT = int(os.environ.get("WJX_PLATFORM_LOG_UPLOAD_IP_LIMIT", "20"))
LICENSE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
DEFAULT_TRIAL_DAYS = int(os.environ.get("WJX_PLATFORM_TRIAL_DAYS", "7"))
OFFLINE_LICENSE_PRIVATE_KEY_PEM = os.environ.get("WJX_OFFLINE_LICENSE_PRIVATE_KEY_PEM", "")
OFFLINE_LICENSE_PRIVATE_KEY_FILE = os.environ.get(
    "WJX_OFFLINE_LICENSE_PRIVATE_KEY_FILE",
    str(DATA_DIR / "offline_license_private.pem"),
)
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
    "feature_automation_unlimited": "1",
    "feature_cluster_projection": "1",
    "sound_library_enabled": "1",
    "update_enabled": "1",
}
SERVICE_QR_TYPES = {
    "wechat": ("微信客服", "点击查看二维码", "微信群二维码 7 天失效时，只需要在后台重新上传"),
    "wechat_group": ("微信交流群", "点击查看二维码", "微信群二维码过期后重新上传，客户端会自动显示最新图"),
}
OFFLINE_LICENSE_FEATURES = [
    "VEHICLE_EVENT_SOUNDS",
    "AUTOMATION_UNLIMITED",
    "CLUSTER_PROJECTION",
    "KEY_SHORTCUT_ACTIONS",
    "DESKTOP_LYRICS",
]
