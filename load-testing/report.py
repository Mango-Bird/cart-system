"""Compare real run artifacts; never manufacture results or certify Kaggle origin."""
import argparse
import csv
import html
import json
import math
from pathlib import Path


def read_run(directory):
    directory = Path(directory)
    summary = json.loads((directory / "summary.json").read_text(encoding="utf-8"))
    metadata = json.loads((directory / "run-metadata.json").read_text(encoding="utf-8"))
    with (directory / "final-stats.csv").open(encoding="utf-8") as handle:
        aggregate = next(row for row in csv.DictReader(handle) if row["Name"] == "Aggregated")
    with (directory / "http-status.csv").open(encoding="utf-8") as handle:
        statuses = list(csv.DictReader(handle))
    total, failures = int(aggregate["Request Count"]), int(aggregate["Failure Count"])
    counts = [int(row["count"]) for row in statuses]
    if (total <= 0 or not 0 <= failures <= total or any(count < 0 for count in counts)
            or any(row["outcome"] not in ("success", "failure") for row in statuses)
            or sum(counts) != total or metadata.get("http_status_count") != total
            or summary["total_requests"] != total or summary["failures"] != failures
            or sum(int(row["count"]) for row in statuses if row["outcome"] == "failure") != failures):
        raise ValueError("Final CSV, HTTP status and metadata counts disagree")
    numeric = {"rps": "Requests/s", "average_latency_ms": "Average Response Time",
               "p95_latency_ms": "95%", "p99_latency_ms": "99%"}
    for key, column in numeric.items():
        value = float(aggregate[column])
        if not math.isfinite(value) or value < 0 or not math.isclose(value, summary[key], rel_tol=1e-9):
            raise ValueError("Latency/RPS summary disagrees with final CSV")
    if not math.isclose(summary["failure_rate"], failures / total, rel_tol=1e-9):
        raise ValueError("Failure rate disagrees with raw counters")
    duration = metadata["duration_seconds"]
    if not math.isfinite(duration) or duration <= 0:
        raise ValueError("Invalid observed duration")
    execution_file = directory / "execution.json"
    execution = json.loads(execution_file.read_text(encoding="utf-8")) if execution_file.exists() else {}
    valid_completion = execution.get("status") in (None, "COMPLETED") and summary["locust_exit_code"] == 0
    origin = summary.get("measurement_origin", "unknown")
    row = {"run": directory.name, "origin_declared": origin,
           "execution": "EXECUTED" if valid_completion else "FAILED / INCOMPLETE",
           "verification": "NOT VERIFIED — review runtime evidence separately",
           "configured_users": metadata["configured_users"],
           "peak_observed_users": metadata["peak_observed_users"],
           "duration_seconds": duration, "total_requests": total,
           **{key: float(aggregate[column]) for key, column in numeric.items()},
           "failure_rate": failures / total,
           "http_status_distribution": json.dumps(statuses, ensure_ascii=False),
           "cache_hit_miss": "NOT MEASURED"}
    # No app-specific cache instrumentation exists. Never infer hits from latency
    # or Redis-wide INFO counters which include unrelated requests/keys.
    return row


def collect(root, output):
    root, output = Path(root), Path(output)
    output.mkdir(parents=True, exist_ok=True)
    rows, errors = [], []
    for directory in sorted(root.iterdir()):
        if not directory.is_dir():
            continue
        if not any((directory / name).exists() for name in ("summary.json", "execution.json", "final-stats.csv")):
            continue
        try:
            rows.append(read_run(directory))
        except (OSError, ValueError, KeyError, StopIteration, TypeError):
            errors.append({"run": directory.name, "status": "INVALID / INCOMPLETE ARTIFACTS"})
    result = {"runs": rows, "invalid_runs": errors,
              "kaggle_benchmark": "NOT VERIFIED — actual Kaggle CPU run and runtime evidence must be reviewed"}
    (output / "comparison.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    # Emit a header even with no results; never fabricate metric rows.
    fields = list(rows[0]) if rows else ["run", "execution", "verification"]
    with (output / "comparison.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    table = "<tr>" + "".join(f"<th>{html.escape(field)}</th>" for field in fields) + "</tr>"
    for row in rows:
        table += "<tr>" + "".join(f"<td>{html.escape(str(row[field]))}</td>" for field in fields) + "</tr>"
    (output / "comparison.html").write_text(
        '<!doctype html><html lang="vi"><meta charset="utf-8"><title>Cart load report</title>'
        '<h1>Real artifacts only — NOT VERIFIED as Kaggle CPU benchmark</h1><table border="1">'
        + table + "</table><pre>" + html.escape(json.dumps(errors, indent=2)) + "</pre></html>", encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    result = collect(args.results, args.output)
    print(f"Analyzed {len(result['runs'])} runs; invalid/incomplete: {len(result['invalid_runs'])}")
    raise SystemExit(1 if result["invalid_runs"] or not result["runs"] else 0)
