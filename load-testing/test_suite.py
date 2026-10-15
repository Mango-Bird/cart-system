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
            (root / "run-metadata.json").write_text(json.dumps({"duration_seconds": 30}))
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

    def test_notebook_valid_and_has_no_executed_outputs(self):
        notebook = nbformat.read(Path(__file__).with_name("kaggle_load_test.ipynb"), as_version=4)
        nbformat.validate(notebook)
        for cell in notebook.cells:
            if cell.cell_type == "code":
                self.assertIsNone(cell.execution_count)
                self.assertEqual([], cell.outputs)
                compile(cell.source, "kaggle cell", "exec")


if __name__ == "__main__":
    unittest.main()
