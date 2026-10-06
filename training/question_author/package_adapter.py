"""Package an experimental adapter with provenance; never mark it app-ready.

Publication is a separate Git operation. This command copies only named model
artifacts and derived synthetic reports, never a model cache or user database.
"""
import argparse
from hashlib import sha256
import json
import math
from pathlib import Path
import shutil


def file_hash(path):
    return sha256(Path(path).read_bytes()).hexdigest()


def run(args):
    if args.output.exists() and any(args.output.iterdir()):
        raise ValueError("Use a new package directory")
    config = json.loads(Path(__file__).with_name("model_config.json").read_text())
    report = json.loads((args.run / "training_report.json").read_text())
    if report["real_user_records_used"] or report["source"] != "synthetic_only":
        raise ValueError("This packaging recipe excludes personal training records")
    if report["base_revision"] != config["base_revision"]:
        raise ValueError("Base revision mismatch")
    weights = args.run / "adapter" / "adapter_model.safetensors"
    if weights.stat().st_size > 50 * 1024 * 1024 or file_hash(weights) != report["adapter_sha256"]:
        raise ValueError("Unexpected adapter size or checksum")
    adapter = json.loads((args.run / "adapter" / "adapter_config.json").read_text())
    if adapter["base_model_name_or_path"] != config["base_model"] or adapter["revision"] != config["base_revision"]:
        raise ValueError("Adapter must reference the pinned public base, not a local file path")
    manifest = json.loads(args.data_manifest.read_text(encoding="utf-8"))
    if manifest["source"] != "synthetic_only" or any(
        item["sha256"] != report["data_sha256"][split] for split, item in manifest["splits"].items()
    ):
        raise ValueError("Training data provenance mismatch")
    evaluations = []
    reserved = {"manifest.json", "data-manifest.json", "LICENSE.txt", "adapter_config.json",
                "adapter_model.safetensors", "training_report.json", "environment-lock.txt"}
    for path in args.evaluation:
        result = json.loads(path.read_text(encoding="utf-8"))
        if result["source"] != "synthetic_only":
            raise ValueError("Only synthetic evaluation summaries can be packaged")
        if not result.get("complete") or result["summary"]["requests"] != result["requested_rows"]:
            raise ValueError("Incomplete evaluation report")
        if result["data_sha256"] not in report["data_sha256"].values():
            raise ValueError("Evaluation data does not match this experiment")
        if result.get("adapter") and result.get("adapter_sha256") != report["adapter_sha256"]:
            raise ValueError("Evaluation used a different adapter")
        if path.name in reserved:
            raise ValueError("Duplicate/reserved evaluation filename")
        reserved.add(path.name)
        evaluations.append((path.name, result))
    args.output.mkdir(parents=True, exist_ok=True)
    for name in ("adapter_config.json", "adapter_model.safetensors"):
        shutil.copyfile(args.run / "adapter" / name, args.output / name)
    for name in ("training_report.json", "environment-lock.txt"):
        shutil.copyfile(args.run / name, args.output / name)
    shutil.copyfile(args.license, args.output / "LICENSE.txt")
    shutil.copyfile(args.data_manifest, args.output / "data-manifest.json")
    summaries = []
    release_checks = {}
    for name, original in evaluations:
        # Learner-level output is available in local reports; publish aggregates with no local paths.
        result = {k: v for k, v in original.items() if k not in ("results", "adapter")}
        result["adapter_evaluated"] = bool(original["adapter"])
        target = args.output / name
        target.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        summaries.append(target.name)
        test_split = manifest["splits"].get("test", {})
        if (original["data_sha256"] == test_split.get("sha256")
                and original["summary"]["requests"] == test_split.get("rows")):
            checks = {}
            for metric, required in config["release_gates"].items():
                observed = original["summary"].get(metric, original.get(metric))
                passed = observed is True if isinstance(required, bool) else (
                    type(observed) in (int, float) and math.isfinite(observed) and observed >= required)
                checks[metric] = {"observed": observed, "required": required, "passed": passed}
            release_checks[name] = checks
    metadata = {
        "artifact_type": "lora_adapter_requires_base_model", "status": "EXPERIMENTAL",
        "training_complete": report.get("training_complete", len(report.get("epochs", [])) == report.get("training", {}).get("epochs")),
        "production_ready": False, "android_integrated": False, "tablet_offline_verified": False,
        "base_model": config["base_model"], "base_revision": config["base_revision"],
        "base_weights_sha256": report["base_weights_sha256"], "license": config["base_license"],
        "training_source": "authored_synthetic_profiles_only", "personal_records_included": False,
        "evaluation_reports": summaries,
        "release_gate_evaluations": release_checks,
        "release_decision": "NOT_APPROVED_FOR_APP",
        "files": {p.name: {"bytes": p.stat().st_size, "sha256": file_hash(p)} for p in args.output.iterdir() if p.is_file()},
    }
    (args.output / "manifest.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--data-manifest", type=Path, required=True)
    parser.add_argument("--evaluation", type=Path, action="append", default=[])
    parser.add_argument("--license", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args())
