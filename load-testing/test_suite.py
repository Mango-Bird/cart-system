"""Offline checks of load-test tooling; no mocked production implementation."""
import csv
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import nbformat

import prepare
import check_connectivity
import report as analysis_report
from tooling import validate_target, validate_timeout, validate_manifest, duration_seconds
import subprocess

spec = importlib.util.spec_from_file_location("load_runner", Path(__file__).with_name("run.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class ToolingTests(unittest.TestCase):
    def test_target_rejects_credentials_and_paths(self):
        for value in ("https://u:p@example.com", "https://example.com/api", "file:///tmp/x"):
            with self.subTest(value=value), patch.dict(os.environ, {"TARGET_BASE_URL": value}):
                with self.assertRaises(ValueError):
                    prepare.target_url()

    def test_target_accepts_origin(self):
        with patch.dict(os.environ, {"TARGET_BASE_URL": "http://127.0.0.1:8081/"}):
            self.assertEqual("http://127.0.0.1:8081", prepare.target_url())

    def test_preparation_error_does_not_include_body(self):
        class Response:
            status_code = 500
            text = "sensitive implementation details"
        with self.assertRaises(RuntimeError) as error:
            prepare.checked(Response(), (200,))
        self.assertNotIn(Response.text, str(error.exception))

    def test_summary_uses_aggregate_not_average_of_percentiles(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "run-metadata.json").write_text(json.dumps({"duration_seconds": 30, "http_status_count": 100}))
            (root / "http-status.csv").write_text("request,status,outcome,count\nGET products,200,success,98\nGET products,500,failure,2\n")
            fields = ["Name", "Request Count", "Failure Count", "Requests/s", "Average Response Time",
                      "Median Response Time", "95%", "99%"]
            with (root / "final-stats.csv").open("w", newline="") as handle:
                writer = csv.writer(handle)
                writer.writerow(fields)
                writer.writerow(["one endpoint", 10, 0, 1, 2, 2, 5, 10])
                writer.writerow(["Aggregated", 100, 2, 3.3, 8, 4, 20, 30])
            summary = runner.summarize(root, 1)
            self.assertEqual(0.02, summary["failure_rate"])
            self.assertEqual(20, summary["p95_latency_ms"])
            self.assertEqual(1, summary["locust_exit_code"])

    def test_https_target_rejects_loopback_and_ambiguous_origins(self):
        for value in ("http://example.com", "https://localhost.", "https://app.localhost",
                      "https://127.0.0.1", "https://[::1]", "https://192.168.1.2",
                      "https://example.com:0", "https://example.com:99999",
                      "https://example.com/?token=x", "https://example.com/#x"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_target(value, require_https=True)
        self.assertEqual("https://example.com", validate_target("https://example.com/", True))

    def test_timeout_rejects_nonfinite_or_unbounded_values(self):
        for value in ("nan", "inf", "0", "-1", "121"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_timeout(value)
        self.assertEqual(10, validate_timeout("10"))

    def test_duration_is_bounded(self):
        self.assertEqual(180, duration_seconds("3m"))
        for value in ("0", "-1s", "nan", "2h", "3301s"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                duration_seconds(value)

    def test_manifest_requires_unique_usernames_and_identities(self):
        base = {"target": "https://example.com", "product_id": 1,
                "accounts": [{"username": "one", "userId": 1}, {"username": "two", "userId": 2}]}
        validate_manifest(base, base["target"], 2)
        for duplicate in ({"username": "one", "userId": 2}, {"username": "two", "userId": 1}):
            with self.subTest(duplicate=duplicate), self.assertRaises(ValueError):
                validate_manifest({**base, "accounts": [base["accounts"][0], duplicate]}, base["target"], 2)

    def test_connectivity_blocks_private_dns(self):
        with patch("check_connectivity.socket.getaddrinfo", return_value=[(2, 1, 6, "", ("127.0.0.1", 443))]):
            with self.assertRaisesRegex(RuntimeError, "non-public"):
                check_connectivity.check("https://example.com")

    def test_connectivity_rejects_redirect_without_following(self):
        with patch("check_connectivity.socket.getaddrinfo", return_value=[(2, 1, 6, "", ("8.8.8.8", 443))]), \
                patch("check_connectivity.requests.Session") as session:
            session.return_value.__enter__.return_value.get.return_value.status_code = 302
            with self.assertRaisesRegex(RuntimeError, "302"):
                check_connectivity.check("https://example.com")
            for call in session.return_value.__enter__.return_value.get.call_args_list:
                self.assertFalse(call.kwargs["allow_redirects"])

    def test_connectivity_sanitizes_tls_failure(self):
        with patch("check_connectivity.socket.getaddrinfo", return_value=[(2, 1, 6, "", ("8.8.8.8", 443))]), \
                patch("check_connectivity.requests.Session") as session:
            session.return_value.__enter__.return_value.get.side_effect = check_connectivity.requests.exceptions.SSLError("private-details")
            with self.assertRaises(RuntimeError) as error:
                check_connectivity.check("https://example.com")
            self.assertIn("TLS verification failed", str(error.exception))
            self.assertNotIn("private-details", str(error.exception))

    def test_runner_records_timeout_as_incomplete(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            data = root / "accounts.json"
            data.write_text(json.dumps({"target": "https://example.com", "product_id": 1,
                                        "accounts": [{"username": "test-only", "userId": 1}]}))
            output = root / "run"
            env = {"TARGET_BASE_URL": "https://example.com", "LOAD_TEST_DATA": str(data),
                   "LOAD_TEST_PASSWORD": "test-only-not-real"}
            with patch.dict(os.environ, env), patch("sys.argv", ["run.py", "--output", str(output)]), \
                    patch.object(runner.subprocess, "run", side_effect=subprocess.TimeoutExpired("locust", 90)):
                self.assertEqual(2, runner.main())
            self.assertEqual("INCOMPLETE", json.loads((output / "execution.json").read_text())["status"])
            self.assertFalse((output / "summary.json").exists())

    def test_report_never_fabricates_empty_results(self):
        with tempfile.TemporaryDirectory() as temp:
            result = analysis_report.collect(Path(temp), Path(temp) / "out")
            self.assertEqual([], result["runs"])
            self.assertIn("NOT VERIFIED", result["kaggle_benchmark"])

    def test_report_rejects_inconsistent_metrics(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            fixture = root / "tampered"
            fixture.mkdir()
            (fixture / "summary.json").write_text(json.dumps({"total_requests": 2, "failures": 0}))
            (fixture / "run-metadata.json").write_text(json.dumps({"http_status_count": 1}))
            (fixture / "final-stats.csv").write_text("Name,Request Count,Failure Count\nAggregated,2,0\n")
            (fixture / "http-status.csv").write_text("request,status,outcome,count\nGET products,200,success,1\n")
            result = analysis_report.collect(root, root / "out")
            self.assertEqual([], result["runs"])
            self.assertEqual(1, len(result["invalid_runs"]))

    def test_successful_artifacts_do_not_certify_declared_kaggle_origin(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "run-metadata.json").write_text(json.dumps({"duration_seconds": 30,
                "http_status_count": 1, "configured_users": 1, "peak_observed_users": 1}))
            (root / "final-stats.csv").write_text(
                "Name,Request Count,Failure Count,Requests/s,Average Response Time,Median Response Time,95%,99%\n"
                "Aggregated,1,0,0.033,10,10,10,10\n")
            (root / "http-status.csv").write_text("request,status,outcome,count\nGET products,200,success,1\n")
            with patch.dict(os.environ, {"LOAD_TEST_ORIGIN": "kaggle-cpu"}):
                runner.summarize(root, 0)
            row = analysis_report.read_run(root)
            self.assertEqual("EXECUTED", row["execution"])
            self.assertIn("NOT VERIFIED", row["verification"])
            self.assertEqual("NOT MEASURED", row["cache_hit_miss"])

    def test_notebook_valid_and_has_no_executed_outputs(self):
        notebook = nbformat.read(Path(__file__).with_name("kaggle_load_test.ipynb"), as_version=4)
        nbformat.validate(notebook)
        for cell in notebook.cells:
            if cell.cell_type == "code":
                self.assertIsNone(cell.execution_count)
                self.assertEqual([], cell.outputs)
                self.assertNotIn("input(", cell.source)
                self.assertNotIn("getpass(", cell.source)
                compile(cell.source, "kaggle cell", "exec")


if __name__ == "__main__":
    unittest.main()
