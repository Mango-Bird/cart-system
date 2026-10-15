"""Run one bounded profile and generate an explicitly labelled result summary."""
import argparse
import csv
from datetime import datetime, timezone
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import math

from tooling import duration_seconds, validate_manifest, validate_timeout

PROFILES = {"smoke": (1, 1, "30s"), "low": (10, 2, "2m"),
            "medium": (50, 5, "3m"), "high": (100, 10, "5m")}
ROOT = Path(__file__).resolve().parent


def summarize(directory, returncode):
    with (directory / "final-stats.csv").open(encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    aggregate = next(row for row in rows if row["Name"] == "Aggregated")
    metadata = json.loads((directory / "run-metadata.json").read_text(encoding="utf-8"))
    requests = int(aggregate["Request Count"])
    failures = int(aggregate["Failure Count"])
    with (directory / "http-status.csv").open(encoding="utf-8") as handle:
        statuses = list(csv.DictReader(handle))
    if (sum(int(row["count"]) for row in statuses) != requests or
            metadata.get("http_status_count") != requests or requests <= 0 or not 0 <= failures <= requests
            or sum(int(row["count"]) for row in statuses if row["outcome"] == "failure") != failures):
        raise ValueError("Request totals are empty or disagree across final CSV/status/metadata")
    summary = {**metadata, "total_requests": requests, "failures": failures,
               "failure_rate": failures / requests if requests else None,
               "rps": float(aggregate["Requests/s"]),
               "average_latency_ms": float(aggregate["Average Response Time"]),
               "median_latency_ms": float(aggregate["Median Response Time"]),
               "p95_latency_ms": float(aggregate["95%"]),
               "p99_latency_ms": float(aggregate["99%"]),
               "locust_exit_code": returncode,
               "http_status_distribution": statuses,
               "cache_hit_miss": {"status": "NOT MEASURED", "hits": None, "misses": None},
               "verification": "NOT VERIFIED — origin and artifacts require review; no automatic Kaggle certification",
               "measurement_origin": os.environ.get("LOAD_TEST_ORIGIN", "local-not-kaggle"),
               "generator": {"python": platform.python_version(), "os": platform.platform(),
                             "logical_cpus": os.cpu_count(),
                             "locust": importlib.metadata.version("locust")},
               "recorded_at_utc": datetime.now(timezone.utc).isoformat()}
    (directory / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", choices=PROFILES, default="smoke")
    parser.add_argument("--users", type=int)
    parser.add_argument("--spawn-rate", type=float)
    parser.add_argument("--duration")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    from prepare import target_url
    target = target_url()
    users, spawn_rate, duration = PROFILES[args.profile]
    users = args.users if args.users is not None else users
    spawn_rate = args.spawn_rate if args.spawn_rate is not None else spawn_rate
    duration = args.duration or duration
    if users < 1 or not math.isfinite(spawn_rate) or spawn_rate <= 0:
        parser.error("users and spawn rate must be positive")
    try:
        seconds = duration_seconds(duration)
        validate_timeout(os.environ.get("LOAD_TEST_TIMEOUT", "10"))
    except ValueError as error:
        parser.error(str(error))
    manifest = json.loads(Path(os.environ["LOAD_TEST_DATA"]).read_text(encoding="utf-8"))
    try:
        validate_manifest(manifest, target, users)
    except ValueError as error:
        parser.error(str(error))
    if not os.environ.get("LOAD_TEST_PASSWORD"):
        parser.error("LOAD_TEST_PASSWORD is required")
    directory = Path(args.output).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    if any(directory.iterdir()):
        parser.error("Output directory must be empty to prevent mixing runs")
    env = dict(os.environ, LOAD_TEST_OUTPUT=str(directory))
    command = [sys.executable, "-m", "locust", "-f", str(ROOT / "locustfile.py"),
               "--headless", "--host", target, "--users", str(users),
               "--spawn-rate", str(spawn_rate), "--run-time", duration,
               "--stop-timeout", "15", "--only-summary", "--exit-code-on-error", "1",
               "--csv", str(directory / "locust"), "--csv-full-history",
               "--html", str(directory / "report.html")]
    state = {"status": "RUNNING", "profile": args.profile,
             "started_at_utc": datetime.now(timezone.utc).isoformat(),
             "configured_duration_seconds": seconds}
    state_file = directory / "execution.json"
    state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
    try:
        with (directory / "locust.log").open("w", encoding="utf-8") as log:
            result = subprocess.run(command, env=env, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=seconds + 60, check=False)
    except (subprocess.TimeoutExpired, KeyboardInterrupt):
        state["status"] = "INCOMPLETE"
        state["reason"] = "Subprocess timeout or interruption; partial metrics are not a completed run"
        state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
        return 2
    state["status"] = "COMPLETED" if result.returncode == 0 else "FAILED"
    state["locust_exit_code"] = result.returncode
    state["finished_at_utc"] = datetime.now(timezone.utc).isoformat()
    state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
    if not (directory / "final-stats.csv").exists() or not (directory / "run-metadata.json").exists():
        state["status"] = "INVALID ARTIFACTS"
        state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
        raise RuntimeError("No complete metrics; inspect locust.log (not a successful benchmark)")
    try:
        summary = summarize(directory, result.returncode)
    except (ValueError, KeyError, StopIteration):
        state["status"] = "INVALID ARTIFACTS"
        state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
        raise RuntimeError("Invalid/inconsistent metrics; this run cannot be accepted") from None
    print(json.dumps(summary, indent=2))
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
