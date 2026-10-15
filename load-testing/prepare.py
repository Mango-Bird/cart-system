"""Provision isolated test accounts; never store passwords or JWTs in artifacts."""
import argparse
import json
import os
from pathlib import Path
import re

import requests
from tooling import validate_target, validate_timeout


def target_url():
    return validate_target(os.environ.get("TARGET_BASE_URL", ""),
                           os.environ.get("LOAD_TEST_REQUIRE_HTTPS") == "1")


def checked(response, expected):
    if response.status_code not in expected:
        # Do not serialize response bodies, headers, tokens or credentials.
        raise RuntimeError(f"Preparation HTTP {response.status_code}, expected {expected}")
    return response


def prepare(users, output):
    if users < 1:
        raise ValueError("users must be positive")
    base = target_url()
    password = os.environ["LOAD_TEST_PASSWORD"]
    run_id = os.environ["LOAD_TEST_RUN_ID"]
    if not password or not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", run_id):
        raise ValueError("Set password and a run ID of 1..32 letters/digits/_/-")
    timeout = validate_timeout(os.environ.get("LOAD_TEST_TIMEOUT", "10"))
    with requests.Session() as client:
        checked(client.get(base + "/api/health", timeout=timeout, allow_redirects=False), (200,))
        products = checked(client.get(base + "/api/products", timeout=timeout, allow_redirects=False), (200,)).json()
        requested = os.environ.get("LOAD_TEST_PRODUCT_ID", "")
        eligible = [p for p in products if isinstance(p.get("stock"), int) and p["stock"] >= 2
                    and (not requested or p["id"] == int(requested))]
        if not eligible:
            raise RuntimeError("No product with stock >= 2. Seed the test database first.")
        product_id = sorted(eligible, key=lambda p: p["id"])[0]["id"]
        accounts = []
        for index in range(users):
            username = f"lt_{run_id}_{index:05d}"
            body = {"username": username, "password": password}
            checked(client.post(base + "/api/auth/register", json=body, timeout=timeout, allow_redirects=False), (201, 409))
            token = checked(client.post(base + "/api/auth/login", json=body, timeout=timeout, allow_redirects=False), (200,)).json()["token"]
            headers = {"Authorization": "Bearer " + token}
            cart = checked(client.get(base + "/api/cart", headers=headers, timeout=timeout, allow_redirects=False), (200,)).json()
            if any(item["productId"] == product_id for item in cart["items"]):
                checked(client.delete(base + f"/api/cart/items/{product_id}", headers=headers,
                                      timeout=timeout, allow_redirects=False), (200,))
            accounts.append({"username": username, "userId": cart["userId"]})
    if len({a["userId"] for a in accounts}) != users:
        raise RuntimeError("Accounts must own distinct carts")
    manifest = {"target": base, "run_id": run_id, "product_id": product_id, "accounts": accounts}
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Prepared {users} isolated users; product ID {product_id}. No credentials saved.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--users", type=int, required=True)
    parser.add_argument("--output", default="load-testing/results/accounts.json")
    args = parser.parse_args()
    prepare(args.users, args.output)
