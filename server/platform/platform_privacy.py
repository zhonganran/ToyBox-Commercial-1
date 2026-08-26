import re
from urllib.parse import urlsplit

from platform_config import PUBLIC_BASE_URL


HIDDEN_ENDPOINT = b"[server endpoint hidden]"
URL_PATTERN = re.compile(rb"\b(?:https?|wss?)://[^\s<>\"']+", re.IGNORECASE)
BRACKETED_IPV6_PATTERN = re.compile(rb"\[[0-9a-f:]+](?::\d{1,5})?", re.IGNORECASE)
UNBRACKETED_IPV6_PATTERN = re.compile(
    rb"(?<![0-9a-f:])(?=(?:[0-9a-f]*:){3})[0-9a-f:]+(?:%[0-9a-z._-]+)?"
    rb"(?![0-9a-f:])",
    re.IGNORECASE,
)
IPV4_PATTERN = re.compile(
    rb"(?<![\d.])(?:25[0-5]|2[0-4]\d|1?\d?\d)"
    rb"(?:\.(?:25[0-5]|2[0-4]\d|1?\d?\d)){3}"
    rb"(?::\d{1,5})?(?![\d.])"
)
COMMON_SECOND_LEVEL_SUFFIXES = {"ac", "co", "com", "edu", "gov", "net", "org"}


def _registrable_root(value):
    try:
        host = (urlsplit(value).hostname or "").strip(".").lower()
    except ValueError:
        return None
    labels = [label for label in host.split(".") if label]
    if len(labels) < 2:
        return None
    second_level_suffix = len(labels[-1]) == 2 and labels[-2] in COMMON_SECOND_LEVEL_SUFFIXES
    count = 3 if second_level_suffix and len(labels) >= 3 else 2
    return ".".join(labels[-count:])


def _protected_domain_patterns():
    roots = {root for root in (_registrable_root(PUBLIC_BASE_URL),) if root}
    return tuple(
        re.compile(
            rb"(?<![a-z0-9.-])(?:[a-z0-9-]+\.)*"
            + re.escape(root.encode("ascii"))
            + rb"(?![a-z0-9.-])",
            re.IGNORECASE,
        )
        for root in roots
    )


PROTECTED_DOMAIN_PATTERNS = _protected_domain_patterns()


def redact_endpoint_bytes(value):
    redacted = URL_PATTERN.sub(HIDDEN_ENDPOINT, value)
    redacted = BRACKETED_IPV6_PATTERN.sub(HIDDEN_ENDPOINT, redacted)
    redacted = UNBRACKETED_IPV6_PATTERN.sub(HIDDEN_ENDPOINT, redacted)
    redacted = IPV4_PATTERN.sub(HIDDEN_ENDPOINT, redacted)
    for pattern in PROTECTED_DOMAIN_PATTERNS:
        redacted = pattern.sub(HIDDEN_ENDPOINT, redacted)
    return redacted


def redact_endpoint_text(value):
    return redact_endpoint_bytes(str(value or "").encode("utf-8", errors="replace")).decode(
        "utf-8",
        errors="replace",
    )
