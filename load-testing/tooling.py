"""Shared validation for the load generator; no application dependencies."""
import ipaddress
import math
import re
from urllib.parse import urlparse


def validate_target(value, require_https=False):
    value = value.strip().rstrip("/")
    parsed = urlparse(value)
    try:
        port = parsed.port
    except ValueError:
        raise ValueError("Invalid target port") from None
    if (parsed.scheme not in ("http", "https") or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.path or parsed.query or parsed.fragment or "?" in value or "#" in value
            or any(c.isspace() for c in value) or port == 0):
        raise ValueError("Target must be an HTTP(S) origin without credentials, path, query or fragment")
    if require_https:
        if parsed.scheme != "https":
            raise ValueError("Kaggle requires an approved HTTPS origin")
        host = parsed.hostname.lower().rstrip(".")
        if host == "localhost" or host.endswith(".localhost"):
            raise ValueError("Kaggle cannot target Windows localhost")
        try:
            address = ipaddress.ip_address(host)
        except ValueError:
            address = None
        if address is not None and not address.is_global:
            raise ValueError("Kaggle target IP must be publicly routable")
    return value


def validate_timeout(value):
    seconds = float(value)
    if not math.isfinite(seconds) or not 0 < seconds <= 120:
        raise ValueError("HTTP timeout must be finite and in (0, 120] seconds")
    return seconds


def duration_seconds(value):
    match = re.fullmatch(r"([1-9][0-9]*)([smh]?)", value)
    if not match:
        raise ValueError("Duration must be an integer with optional s/m/h suffix")
    seconds = int(match[1]) * {"": 1, "s": 1, "m": 60, "h": 3600}[match[2]]
    if seconds > 3300:
        raise ValueError("Duration must not exceed 55 minutes (leave time for graceful shutdown)")
    return seconds


def validate_manifest(manifest, target, users):
    accounts = manifest.get("accounts")
    if manifest.get("target") != target or not isinstance(accounts, list) or len(accounts) < users:
        raise ValueError("Prepare enough isolated accounts for this exact target first")
    if not isinstance(manifest.get("product_id"), int) or isinstance(manifest["product_id"], bool) or manifest["product_id"] <= 0:
        raise ValueError("Manifest requires a positive product ID")
    names, identities = set(), set()
    for account in accounts:
        name, identity = account.get("username"), account.get("userId")
        if (not isinstance(name, str) or not name or not isinstance(identity, int)
                or isinstance(identity, bool) or identity <= 0 or name in names or identity in identities):
            raise ValueError("Manifest must contain distinct usernames and positive user IDs")
        names.add(name)
        identities.add(identity)
