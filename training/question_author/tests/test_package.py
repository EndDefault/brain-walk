"""Packaging must not silently publish different weights or incomplete results."""
from argparse import Namespace
from contextlib import redirect_stdout
from hashlib import sha256
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_adapter import run


class PackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.args = Namespace(run=root / "run", data_manifest=root / "data.json",
                              evaluation=[root / "evaluation.json"], license=root / "LICENSE.txt",
                              output=root / "package")
        (self.args.run / "adapter").mkdir(parents=True)
        config = json.loads((Path(__file__).resolve().parents[1] / "model_config.json").read_text())
        weights = b"synthetic-test-fixture-not-model-weights"
        (self.args.run / "adapter" / "adapter_model.safetensors").write_bytes(weights)
        self.write(self.args.run / "adapter" / "adapter_config.json", {
            "base_model_name_or_path": config["base_model"], "revision": config["base_revision"]})
        self.report = {
            "real_user_records_used": False, "source": "synthetic_only",
            "base_revision": config["base_revision"], "base_weights_sha256": "base-fixture",
            "adapter_sha256": sha256(weights).hexdigest(), "data_sha256": {"test": "test-fixture"}}
        self.write_report()
        self.write(self.args.data_manifest, {"source": "synthetic_only", "splits": {
            "test": {"sha256": "test-fixture"}}})
        self.evaluation = {
            "source": "synthetic_only", "adapter": "a/local/path",
            "adapter_sha256": self.report["adapter_sha256"], "data_sha256": "test-fixture",
            "complete": True, "requested_rows": 1, "summary": {"requests": 1},
            "results": [{"raw": "learner-level-output"}]}
        self.write_evaluation()
        (self.args.run / "environment-lock.txt").write_text("test fixture\n")
        self.args.license.write_text("test fixture license\n")
        # An unexpected local file must never be copied into the package.
        (self.args.run / "private.db").write_bytes(b"do-not-copy")

    @staticmethod
    def write(path, value):
        path.write_text(json.dumps(value), encoding="utf-8")

    def write_report(self):
        self.write(self.args.run / "training_report.json", self.report)

    def write_evaluation(self):
        self.write(self.args.evaluation[0], self.evaluation)

    def test_package_is_experimental_and_uses_named_files_only(self):
        with redirect_stdout(io.StringIO()):
            run(self.args)
        manifest = json.loads((self.args.output / "manifest.json").read_text())
        self.assertFalse(manifest["production_ready"])
        self.assertFalse(manifest["android_integrated"])
        self.assertFalse((self.args.output / "private.db").exists())
        summary = json.loads((self.args.output / "evaluation.json").read_text())
        self.assertNotIn("results", summary)
        self.assertNotIn("adapter", summary)
        for name, record in manifest["files"].items():
            self.assertEqual(record["sha256"], sha256((self.args.output / name).read_bytes()).hexdigest())
        with self.assertRaisesRegex(ValueError, "new package"):
            run(self.args)

    def test_modified_weights_are_rejected_before_copy(self):
        (self.args.run / "adapter" / "adapter_model.safetensors").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "checksum"):
            run(self.args)
        self.assertFalse(self.args.output.exists())

    def test_personal_data_recipe_is_rejected(self):
        self.report["real_user_records_used"] = True
        self.write_report()
        with self.assertRaisesRegex(ValueError, "personal"):
            run(self.args)

    def test_incomplete_or_wrong_adapter_evaluation_is_rejected(self):
        self.evaluation["complete"] = False
        self.write_evaluation()
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            run(self.args)
        self.evaluation.update(complete=True, adapter_sha256="different")
        self.write_evaluation()
        with self.assertRaisesRegex(ValueError, "different adapter"):
            run(self.args)
        self.assertFalse(self.args.output.exists())

    def test_mismatched_data_is_rejected(self):
        self.evaluation["data_sha256"] = "different"
        self.write_evaluation()
        with self.assertRaisesRegex(ValueError, "Evaluation data"):
            run(self.args)

    def test_format_success_does_not_hide_failed_focus_and_device_gates(self):
        self.write(self.args.data_manifest, {"source": "synthetic_only", "splits": {
            "test": {"sha256": "test-fixture", "rows": 1}}})
        self.evaluation["summary"].update(structural_valid_rate=1., focus_accuracy=.6,
                                         no_recent_or_repeated_target_rate=1., confusion_review_rate=.8)
        self.evaluation["tablet_offline_verified"] = False
        self.write_evaluation()
        with redirect_stdout(io.StringIO()):
            run(self.args)
        manifest = json.loads((self.args.output / "manifest.json").read_text())
        checks = manifest["release_gate_evaluations"]["evaluation.json"]
        self.assertTrue(checks["structural_valid_rate"]["passed"])
        self.assertFalse(checks["focus_accuracy"]["passed"])
        self.assertFalse(checks["tablet_offline_verified"]["passed"])
        self.assertEqual("NOT_APPROVED_FOR_APP", manifest["release_decision"])
        self.assertFalse(manifest["production_ready"])


if __name__ == "__main__":
    unittest.main()
