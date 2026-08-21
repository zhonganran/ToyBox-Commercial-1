import base64
import binascii
import json
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

from platform_config import (
    PAYMENT_GATEWAY_BASE_URL,
    PAYMENT_HTTP_TIMEOUT_SECONDS,
    PAYMENT_MERCHANT_PRIVATE_KEY_FILE,
    PAYMENT_MERCHANT_PRIVATE_KEY_PEM,
    PAYMENT_PID,
    PAYMENT_PLATFORM_PUBLIC_KEY_FILE,
    PAYMENT_PLATFORM_PUBLIC_KEY_PEM,
)


class PaymentConfigurationError(RuntimeError):
    pass


class PaymentProviderError(RuntimeError):
    pass


def canonicalize(params):
    values = []
    for key in sorted(params):
        # XPay V2 signs business fields only. The response envelope's
        # `code` and algorithm marker are metadata, not signed fields.
        if key in {"code", "sign", "sign_type"}:
            continue
        value = params[key]
        if value is None or value == "":
            continue
        values.append(f"{key}={value}")
    return "&".join(values)


def _pem_bytes(inline_value, file_value, label):
    raw = str(inline_value or "").strip()
    if not raw and file_value:
        path = Path(file_value)
        if path.is_file():
            raw = path.read_text(encoding="utf-8").strip()
    if not raw:
        raise PaymentConfigurationError(f"{label} is not configured")
    raw = raw.replace("\\n", "\n").strip()
    if raw.startswith("-----BEGIN"):
        return (raw + ("\n" if not raw.endswith("\n") else "")).encode("ascii")
    compact = "".join(raw.split())
    header = "PRIVATE KEY" if "private" in label.lower() else "PUBLIC KEY"
    wrapped = "\n".join(compact[index:index + 64] for index in range(0, len(compact), 64))
    return f"-----BEGIN {header}-----\n{wrapped}\n-----END {header}-----\n".encode("ascii")


def load_merchant_private_key():
    pem = _pem_bytes(
        PAYMENT_MERCHANT_PRIVATE_KEY_PEM,
        PAYMENT_MERCHANT_PRIVATE_KEY_FILE,
        "payment merchant private key",
    )
    try:
        return serialization.load_pem_private_key(pem, password=None)
    except (TypeError, ValueError) as exc:
        raise PaymentConfigurationError("payment merchant private key is invalid") from exc


def load_platform_public_key():
    pem = _pem_bytes(
        PAYMENT_PLATFORM_PUBLIC_KEY_PEM,
        PAYMENT_PLATFORM_PUBLIC_KEY_FILE,
        "payment platform public key",
    )
    try:
        return serialization.load_pem_public_key(pem)
    except (TypeError, ValueError) as exc:
        raise PaymentConfigurationError("payment platform public key is invalid") from exc


def payment_ready():
    if not PAYMENT_PID:
        return False
    try:
        load_merchant_private_key()
        load_platform_public_key()
    except PaymentConfigurationError:
        return False
    return True


def sign_params(params, private_key=None):
    key = private_key or load_merchant_private_key()
    signature = key.sign(
        canonicalize(params).encode("utf-8"),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )
    return base64.b64encode(signature).decode("ascii")


def verify_params(params, public_key=None):
    signature_text = str(params.get("sign") or "").strip()
    if not signature_text:
        return False
    try:
        signature = base64.b64decode(signature_text, validate=True)
        key = public_key or load_platform_public_key()
        key.verify(
            signature,
            canonicalize(params).encode("utf-8"),
            padding.PKCS1v15(),
            hashes.SHA256(),
        )
        return True
    except (InvalidSignature, binascii.Error, TypeError, ValueError):
        return False


def signed_params(params):
    payload = {
        key: str(value)
        for key, value in params.items()
        if value is not None and value != ""
    }
    payload["pid"] = PAYMENT_PID
    payload["timestamp"] = str(int(time.time()))
    payload["sign_type"] = "RSA"
    payload["sign"] = sign_params(payload)
    return payload


def _post(path, params):
    payload = signed_params(params)
    request = Request(
        f"{PAYMENT_GATEWAY_BASE_URL}/{path.lstrip('/')}",
        data=urlencode(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/x-www-form-urlencoded; charset=utf-8",
            "Accept": "application/json",
            "User-Agent": "SuperToyBox-Payment/2",
        },
        method="POST",
    )
    try:
        with urlopen(request, timeout=PAYMENT_HTTP_TIMEOUT_SECONDS) as response:
            raw = response.read(256 * 1024).decode("utf-8", errors="replace")
    except HTTPError as exc:
        raw = exc.read(16 * 1024).decode("utf-8", errors="replace")
        raise PaymentProviderError(f"payment provider HTTP {exc.code}: {raw[:160]}") from exc
    except (URLError, TimeoutError) as exc:
        raise PaymentProviderError("payment provider is unavailable") from exc
    try:
        result = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise PaymentProviderError("payment provider returned invalid JSON") from exc
    if not isinstance(result, dict):
        raise PaymentProviderError("payment provider returned an invalid response")
    if int(result.get("code", -1)) != 0:
        raise PaymentProviderError(str(result.get("msg") or "payment provider rejected the request"))
    if not verify_params(result):
        raise PaymentProviderError("payment provider response signature is invalid")
    return result


def create_order(params):
    return _post("api/pay/create", params)


def query_order(out_trade_no):
    return _post("api/pay/query", {"out_trade_no": out_trade_no})
