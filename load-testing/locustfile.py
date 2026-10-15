"""Single-process workload; prepare accounts before starting the measured run."""
import base64
from collections import Counter, deque
import csv
import json
import os
from pathlib import Path
import time

from locust import HttpUser, between, events, task
from locust.exception import StopUser
from tooling import validate_timeout

_accounts = deque()
_statuses = Counter()
_started = 0.0
_peak_users = 0
_manifest = {}


@events.test_start.add_listener
def start(environment, **kwargs):
    global _manifest, _started, _peak_users
    _manifest = json.loads(Path(os.environ["LOAD_TEST_DATA"]).read_text(encoding="utf-8"))
    if environment.host.rstrip("/") != _manifest["target"]:
        raise ValueError("Account manifest belongs to another target")
    if not os.environ.get("LOAD_TEST_PASSWORD"):
        raise ValueError("LOAD_TEST_PASSWORD is required")
    _accounts.clear()
    _accounts.extend(_manifest["accounts"])
    _statuses.clear()
    _peak_users = 0
    _started = time.monotonic()


@events.request.add_listener
def record_status(response=None, name="", exception=None, **kwargs):
    global _peak_users
    status = str(getattr(response, "status_code", 0) or "transport_error")
    outcome = "failure" if exception else "success"
    _statuses[(name, status, outcome)] += 1
    if _environment.runner:
        _peak_users = max(_peak_users, _environment.runner.user_count)


@events.init.add_listener
def initialize(environment, **kwargs):
    global _environment
    _environment = environment


@events.quitting.add_listener
def write_metrics(environment, **kwargs):
    directory = Path(os.environ.get("LOAD_TEST_OUTPUT", "load-testing/results"))
    directory.mkdir(parents=True, exist_ok=True)
    # Locust's periodic CSV may precede the final requests during graceful stop.
    # Snapshot the in-memory counters after shutdown for authoritative totals.
    with (directory / "final-stats.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["Name", "Request Count", "Failure Count", "Requests/s",
                         "Average Response Time", "Median Response Time", "95%", "99%"])
        for name, entry in [(key[0], value) for key, value in environment.stats.entries.items()] + [
                ("Aggregated", environment.stats.total)]:
            writer.writerow([name, entry.num_requests, entry.num_failures, entry.total_rps,
                             entry.avg_response_time, entry.median_response_time,
                             entry.get_response_time_percentile(0.95) if entry.num_requests else 0,
                             entry.get_response_time_percentile(0.99) if entry.num_requests else 0])
    with (directory / "http-status.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["request", "status", "outcome", "count"])
        for (name, status, outcome), count in sorted(_statuses.items()):
            writer.writerow([name, status, outcome, count])
    metadata = {"duration_seconds": round(time.monotonic() - _started, 3),
                "peak_observed_users": _peak_users,
                "configured_users": environment.parsed_options.num_users,
                "spawn_rate": environment.parsed_options.spawn_rate,
                "run_time": environment.parsed_options.run_time,
                "http_status_count": sum(_statuses.values()),
                "scope": "single-process; includes login and relogin; preparation excluded"}
    if sum(_statuses.values()) != environment.stats.total.num_requests or not environment.stats.total.num_requests:
        environment.process_exit_code = 2
    (directory / "run-metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")


class CartUser(HttpUser):
    wait_time = between(0.5, 1.5)

    def on_start(self):
        if not _accounts:
            self.environment.process_exit_code = 2
            raise StopUser("Not enough prepared accounts")
        self.account = _accounts.popleft()
        self.product_id = _manifest["product_id"]
        self.timeout = validate_timeout(os.environ.get("LOAD_TEST_TIMEOUT", "10"))
        self.token_expiry = 0
        self.login()

    def login(self):
        with self.client.post("/api/auth/login", name="AUTH login", timeout=self.timeout,
                              json={"username": self.account["username"],
                                    "password": os.environ["LOAD_TEST_PASSWORD"]},
                              catch_response=True, allow_redirects=False) as response:
            try:
                if response.status_code != 200:
                    raise ValueError()
                token = response.json()["token"]
                payload = token.split(".")[1]
                # Scheduling only: signature validation remains server-side.
                claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
                self.token_expiry = float(claims["exp"])
                self.client.headers["Authorization"] = "Bearer " + token
                response.success()
            except (ValueError, KeyError, IndexError, TypeError):
                response.failure("Login failed or malformed token")
                self.environment.process_exit_code = 2
                raise StopUser()

    def request(self, method, path, name, **kwargs):
        if time.time() >= self.token_expiry - 30:
            self.login()
        for attempt in range(2):
            with self.client.request(method, path, name=name, timeout=self.timeout,
                                     catch_response=True, allow_redirects=False, **kwargs) as response:
                retry = response.status_code == 401 and attempt == 0
                if response.status_code == 200:
                    try:
                        body = response.json()
                        if name == "GET products":
                            valid = isinstance(body, list)
                        elif name == "GET product detail":
                            valid = body.get("id") == self.product_id
                        else:
                            valid = body.get("userId") == self.account["userId"] and isinstance(body.get("items"), list)
                        if not valid:
                            raise ValueError()
                        response.success()
                        return True
                    except (ValueError, AttributeError):
                        response.failure("Unexpected response DTO")
                else:
                    response.failure(f"Unexpected HTTP {response.status_code or 'transport error'}")
            if retry:
                self.login()
            else:
                return False
        return False

    @task(4)
    def products(self):
        self.request("GET", "/api/products", "GET products")

    @task(3)
    def product(self):
        self.request("GET", f"/api/products/{self.product_id}", "GET product detail")

    @task(4)
    def cart(self):
        self.request("GET", "/api/cart", "GET cart")

    @task(2)
    def write_cycle(self):
        path = f"/api/cart/items/{self.product_id}"
        if not self.request("POST", "/api/cart/items", "POST cart item",
                            json={"productId": self.product_id, "quantity": 1}):
            return
        self.request("PUT", path, "PUT cart item", json={"quantity": 2})
        if not self.request("DELETE", path, "DELETE cart item"):
            # Avoid repeated adds on an item whose server state is uncertain.
            self.environment.process_exit_code = 2
            raise StopUser()
