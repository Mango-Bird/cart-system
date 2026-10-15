"""Read-only Kaggle HTTPS preflight; does not deploy or open network ports."""
import argparse
from datetime import datetime, timezone
import ipaddress
import json
import os
from pathlib import Path
import socket
from urllib.parse import urlparse

import requests

from tooling import validate_target, validate_timeout


def check(base, timeout=10, require_https=True):
    base = validate_target(base, require_https)
    timeout = validate_timeout(timeout)
    host = urlparse(base)
    try:
        addresses = socket.getaddrinfo(host.hostname, host.port or (443 if host.scheme == "https" else 80), type=socket.SOCK_STREAM)
    except OSError:
        raise RuntimeError("DNS resolution failed; check hostname and Kaggle Internet access") from None
    if require_https and any(not ipaddress.ip_address(info[4][0]).is_global for info in addresses):
        raise RuntimeError("DNS resolves to a non-public IP; Kaggle cannot reach Windows/private networking")
    with requests.Session() as client:
        try:
            health = client.get(base + "/api/health", timeout=timeout, allow_redirects=False)
            products = client.get(base + "/api/products", timeout=timeout, allow_redirects=False)
        except requests.exceptions.SSLError:
            raise RuntimeError("TLS verification failed; configure a valid certificate, do not disable verification") from None
        except requests.exceptions.Timeout:
            raise RuntimeError("HTTP timeout; check routing, firewall and backend readiness") from None
        except requests.exceptions.RequestException:
            raise RuntimeError("HTTP connection failed; check approved endpoint and Kaggle Internet access") from None
    for response in (health, products):
        if response.status_code != 200:
            raise RuntimeError(f"Preflight HTTP {response.status_code}; redirects/proxy login are not accepted")
    try:
        healthy = health.json() == {"status": "UP"}
        catalogue = products.json()
        eligible = isinstance(catalogue, list) and any(
            isinstance(p, dict) and isinstance(p.get("stock"), int) and p["stock"] >= 2 for p in catalogue)
    except ValueError:
        raise RuntimeError("Expected API JSON, received another response format") from None
    if not healthy or not eligible:
        raise RuntimeError("Health DTO invalid or no seeded product with stock >= 2")
    return {"status": "PASS", "checked_at_utc": datetime.now(timezone.utc).isoformat(),
            "target": base, "https_required": require_https, "tls_verification": host.scheme == "https",
            "redirects_allowed": False, "health_status": 200, "products_status": 200,
            "note": "Read-only preflight, not a benchmark or DB/Redis readiness guarantee"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--allow-local-http", action="store_true", help="Local validation only, never Kaggle")
    args = parser.parse_args()
    try:
        result = check(os.environ.get("TARGET_BASE_URL", ""),
                       os.environ.get("LOAD_TEST_TIMEOUT", "10"), not args.allow_local_http)
    except (ValueError, RuntimeError) as error:
        result = {"status": "FAIL", "reason": str(error)}
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print("Connectivity:", result["status"])
    if result["status"] != "PASS":
        print(result["reason"])
        raise SystemExit(1)
