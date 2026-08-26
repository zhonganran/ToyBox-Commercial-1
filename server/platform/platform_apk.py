import hashlib
import re
from dataclasses import dataclass

from androguard.core.apk import APK
from loguru import logger


EXPECTED_PACKAGE_NAME = "com.byd.wjx"
RELEASE_VERSION = re.compile(
    r"[0-9]+(?:\.[0-9]+){1,3}(?:[-+][0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"
)


class ApkValidationError(ValueError):
    pass


@dataclass(frozen=True)
class ApkMetadata:
    package_name: str
    version_name: str
    version_code: int
    sha256: str
    certificate_sha256: str


def is_valid_release_version(value):
    text = str(value or "")
    return 3 <= len(text) <= 64 and RELEASE_VERSION.fullmatch(text) is not None


def inspect_apk(path):
    try:
        logger.disable("androguard")
        apk = APK(str(path), testzip=True)
        package_name = str(apk.get_package() or "").strip()
        version_name = str(apk.get_androidversion_name() or "").strip().removeprefix("v")
        version_code = int(str(apk.get_androidversion_code() or "0"))
        if not (apk.is_signed_v2() or apk.is_signed_v3()):
            raise ApkValidationError("APK 必须包含 v2 或 v3 签名")
        certificate_der = []
        for certificate in apk.get_certificates():
            encoded = certificate.dump()
            if encoded not in certificate_der:
                certificate_der.append(encoded)
    except ApkValidationError:
        raise
    except Exception as exc:
        raise ApkValidationError("无法解析 APK 文件") from exc

    if package_name != EXPECTED_PACKAGE_NAME:
        raise ApkValidationError(f"APK 包名必须是 {EXPECTED_PACKAGE_NAME}")
    if not is_valid_release_version(version_name) or version_code <= 0:
        raise ApkValidationError("APK 缺少有效版本信息")
    if len(certificate_der) != 1:
        raise ApkValidationError("APK 必须包含且仅包含一个签名证书")

    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return ApkMetadata(
        package_name=package_name,
        version_name=version_name,
        version_code=version_code,
        sha256=digest.hexdigest(),
        certificate_sha256=hashlib.sha256(certificate_der[0]).hexdigest(),
    )
